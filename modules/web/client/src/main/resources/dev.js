import "resources/theme/semantic.less";
import "./less/style.less";
import "./less/semantic-ui-alerts.less";

var App = require("sjs/engage_web_client-fastopt.js");

if (module.hot) {
  module.hot.dispose(() => {
    App.EngageApp.stop();
  });
  module.hot.accept();
  App.EngageApp.start();
}
