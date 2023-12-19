// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.Parallel
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.effect.syntax.all.*
import cats.syntax.all.*
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import com.comcast.ip4s.Dns
import fs2.Stream
import fs2.compression.Compression
import fs2.concurrent.Topic
import fs2.io.file.Files
import fs2.io.net.Network
import natchez.Trace.Implicits.noop
import navigate.model.NavigateEvent
import navigate.model.config.*
import navigate.server.CaServiceInit
import navigate.server.NavigateEngine
import navigate.server.NavigateFailure
import navigate.server.Systems
import navigate.server.tcs.GuideState
import navigate.web.server.OcsBuildInfo
import navigate.web.server.common.LogInitialization
import navigate.web.server.common.RedirectToHttpsRoutes
import navigate.web.server.common.StaticRoutes
import navigate.web.server.common.baseDir
import navigate.web.server.config.*
import navigate.web.server.logging.SubscriptionAppender
import navigate.web.server.security.AuthenticationService
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Router
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.http4s.server.Server
import org.http4s.server.middleware.Logger as Http4sLogger
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigObjectSource
import pureconfig.ConfigSource

import java.io.FileInputStream
import java.nio.file.Path as FilePath
import java.security.KeyStore
import java.security.Security
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import scala.concurrent.duration.*

object WebServerLauncher extends IOApp with LogInitialization:
  private val ProxyRoute: Uri.Path = Uri.Path.empty / "db"

  private given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("navigate")

  // Attempt to get the configuration file relative to the base dir
  def configurationFile[F[_]: Sync]: F[FilePath] =
    baseDir[F].map(_.resolve("conf").resolve("app.conf"))

  def config[F[_]: Sync]: F[ConfigObjectSource] =
    val defaultConfig = ConfigSource.resources("app.conf").pure[F]
    val fileConfig    = configurationFile.map(ConfigSource.file)

    // ConfigSource, first attempt the file or default to the classpath/resources file
    (fileConfig, defaultConfig).mapN: (file, default) =>
      file.optional.withFallback(default.optional)

  /** Configures the Authentication service */
  def authService[F[_]: Sync: Logger](
    mode: Mode,
    conf: AuthenticationConfig
  ): F[AuthenticationService[F]] =
    Sync[F].delay(AuthenticationService[F](mode, conf))

  def makeContext[F[_]: Sync](tls: TLSConfig): F[SSLContext] = Sync[F].delay {
    val ksStream   = new FileInputStream(tls.keyStore.toFile.getAbsolutePath)
    val ks         = KeyStore.getInstance("JKS")
    ks.load(ksStream, tls.keyStorePwd.toCharArray)
    ksStream.close()
    val trustStore = StoreInfo(tls.keyStore.toFile.getAbsolutePath, tls.keyStorePwd)

    val tmf = {
      val ksStream = new FileInputStream(trustStore.path)

      val ks = KeyStore.getInstance("JKS")
      ks.load(ksStream, tls.keyStorePwd.toCharArray)
      ksStream.close()

      val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

      tmf.init(ks)
      tmf.getTrustManagers
    }

    val kmf = KeyManagerFactory.getInstance(
      Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
        .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
    )

    kmf.init(ks, tls.certPwd.toCharArray)

    val context = SSLContext.getInstance("TLS")
    context.init(kmf.getKeyManagers, tmf, null)
    context
  }

  /** Resource that yields the running web server */
def webServer[F[_]: Logger: Async: Dns: Files: Compression: Network](
    conf:       NavigateConfiguration,
    as:         AuthenticationService[F],
    outputs:    Topic[F, NavigateEvent],
    logTopic:   Topic[F, ILoggingEvent],
    guideTopic: Topic[F, GuideState],
    se:         NavigateEngine[F],
    clientsDb:  ClientsSetDb[F]
  ): Resource[F, Server] = {
    val ssl: F[Option[SSLContext]] = conf.webServer.tls.map(makeContext[F]).sequence

    def router(wsBuilder: WebSocketBuilder2[F], proxyService: HttpRoutes[F]) = Router[F](
      "/"                 -> new StaticRoutes().service,
      "/navigate"         -> new GraphQlRoutes(se, logTopic, guideTopic).service(wsBuilder),
      ProxyRoute.toString -> proxyService
    )

    val pingRouter = Router[F](
      "/ping" -> new PingRoutes(as).service
    )

    def loggedRoutes(wsBuilder: WebSocketBuilder2[F], proxyService: HttpRoutes[F]) =
      pingRouter <+>
        Http4sLogger.httpRoutes(logHeaders = false, logBody = false)(
          router(wsBuilder, proxyService)
        )

    def builder(proxyService: HttpRoutes[F]) =
      BlazeServerBuilder[F]
        .bindHttp(conf.webServer.port, conf.webServer.host)
        .withHttpWebSocketApp(wsb => loggedRoutes(wsb, proxyService).orNotFound)

    for
      proxyService <- ProxyBuilder.buildService[F](conf.webServer.proxyBaseUri, ProxyRoute)
      server       <- ssl
                        .map(_.fold(builder(proxyService))(builder(proxyService).withSslContext).resource)
                        .toResource
                        .flatten
    yield server
  }

  def redirectWebServer[F[_]: Async](conf: WebServerConfiguration): Resource[F, Server] = {
    val router = Router[F](
      "/" -> new RedirectToHttpsRoutes[F](443, conf.externalBaseUrl).service
    )

    BlazeServerBuilder[F]
      .bindHttp(conf.insecurePort, conf.host)
      .withHttpApp(router.orNotFound)
      .resource
  }

  def printBanner[F[_]: Logger](conf: NavigateConfiguration): F[Unit] = {
    val banner = """
    _   __            _             __
   / | / /___ __   __(_)___ _____ _/ /____
  /  |/ / __ `/ | / / / __ `/ __ `/ __/ _ \
 / /|  / /_/ /| |/ / / /_/ / /_/ / /_/  __/
/_/ |_/\__,_/ |___/_/\__, /\__,_/\__/\___/
                    /____/

"""
    val msg    =
      s"""Start web server for site ${conf.site} on ${conf.mode} mode, version ${OcsBuildInfo.version}"""
    Logger[F].info(banner + msg)
  }

  // We need to manually update the configuration of the logging subsystem
  // to support capturing log messages and forward them to the clients
  def logToClients(
    out:        Topic[IO, ILoggingEvent],
    dispatcher: Dispatcher[IO]
  ): IO[Appender[ILoggingEvent]] = IO.apply {
    import ch.qos.logback.classic.{AsyncAppender, Logger, LoggerContext}
    import org.slf4j.LoggerFactory

    val asyncAppender = new AsyncAppender
    val appender      = new SubscriptionAppender[IO](out)(using dispatcher)
    Option(LoggerFactory.getILoggerFactory)
      .collect { case lc: LoggerContext =>
        lc
      }
      .foreach { ctx =>
        asyncAppender.setContext(ctx)
        appender.setContext(ctx)
        asyncAppender.addAppender(appender)
      }

    Option(LoggerFactory.getLogger("navigate"))
      .collect { case l: Logger =>
        l
      }
      .foreach { l =>
        l.addAppender(asyncAppender)
        asyncAppender.start()
        appender.start()
      }
    asyncAppender
  }

  // Logger of error of last resort.
  def logError[F[_]: Logger]: PartialFunction[Throwable, F[Unit]] = {
    case e: NavigateFailure =>
      Logger[F].error(e)(s"Navigate global error handler ${NavigateFailure.explain(e)}")
    case e: Exception       => Logger[F].error(e)("Navigate global error handler")
  }

  /** Reads the configuration and launches the navigate engine and web server */
  def navigate[I]: IO[ExitCode] = {

    // Override the default client config
    def client(timeout: Duration): Resource[IO, Client[IO]] =
      EmberClientBuilder.default[IO].withTimeout(timeout).build

    def engineIO(
      conf:       NavigateConfiguration,
      httpClient: Client[IO]
    ): Resource[IO, NavigateEngine[IO]] =
      for {
        dspt <- Dispatcher.sequential[IO]
        cas  <- CaServiceInit.caInit[IO](conf.navigateEngine)
        sys  <-
          Systems
            .build[IO](conf.site, httpClient, conf.navigateEngine, cas)(Async[IO],
                                                                        dspt,
                                                                        Parallel[IO]
            )
        seqE <- Resource.eval[IO, NavigateEngine[IO]](
                  NavigateEngine.build[IO](conf.site, sys, conf.navigateEngine)
                )
      } yield seqE

    def webServerIO(
      conf:  NavigateConfiguration,
      out:   Topic[IO, NavigateEvent],
      log:   Topic[IO, ILoggingEvent],
      guide: Topic[IO, GuideState],
      en:    NavigateEngine[IO],
      cs:    ClientsSetDb[IO]
    ): Resource[IO, Unit] =
      for {
        as <- Resource.eval(authService[IO](conf.mode, conf.authentication))
        _  <- webServer[IO](conf, as, out, log, guide, en, cs)
      } yield ()

    def publishStats[F[_]: Temporal](cs: ClientsSetDb[F]): Stream[F, Unit] =
      Stream.fixedRate[F](10.minute).flatMap(_ => Stream.eval(cs.report))

    val navigate: Resource[IO, ExitCode] =
      for {
        _      <- Resource.eval(IO.delay(Locale.setDefault(Locale.ENGLISH)))
        _      <- Resource.eval(configLog[IO]) // Initialize log before the engine is setup
        conf   <- Resource.eval(config[IO].flatMap(loadConfiguration[IO]))
        _      <- Resource.eval(printBanner(conf))
        cli    <- client(10.seconds)
        out    <- Resource.eval(Topic[IO, NavigateEvent])
        log    <- Resource.eval(Topic[IO, ILoggingEvent])
        gd     <- Resource.eval(Topic[IO, GuideState])
        dsp    <- Dispatcher.sequential[IO]
        _      <- Resource.eval(logToClients(log, dsp))
        cs     <- Resource.eval(
                    Ref.of[IO, ClientsSetDb.ClientsSet](Map.empty).map(ClientsSetDb.apply[IO](_))
                  )
        _      <- Resource.eval(publishStats(cs).compile.drain.start)
        engine <- engineIO(conf, cli)
        _      <- webServerIO(conf, out, log, gd, engine, cs)
        _      <- Resource.eval(
                    out.subscribers
                      .evalMap(l => Logger[IO].debug(s"Subscribers amount: $l").whenA(l > 1))
                      .compile
                      .drain
                      .start
                  )
        _      <- Resource.eval(
                    guideStatePoll(engine, gd).compile.drain.start
                  )
        f      <- Resource.eval(
                    engine.eventStream.through(out.publish).compile.drain.onError(logError).start
                  )
        _      <- Resource.eval(f.join)        // We need to join to catch uncaught errors
      } yield ExitCode.Success

    navigate.use(_ => IO.never)

  }

  /** Reads the configuration and launches Navigate */
  override def run(args: List[String]): IO[ExitCode] =
    navigate.guaranteeCase { oc =>
      if (oc.isSuccess) IO.unit
      else IO(Console.println(s"Exit code $oc")) // scalastyle:off console.io
    }

  def guideStatePoll(eng: NavigateEngine[IO], t: Topic[IO, GuideState]): Stream[IO, Unit] =
    Stream
      .fixedRate[IO](FiniteDuration(1, TimeUnit.SECONDS))
      .evalMap(_ => eng.getGuideState)
      .evalMapAccumulate(none) { (acc, gs) =>
        (if (acc.contains(gs)) IO.unit else t.publish1(gs).void).as(gs.some, ())
      }
      .void

}
