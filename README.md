# navigate

New telescope control tool for the Gemini Observatory

# How to package for deployment

If you haven't already, create a symlink `jre` in the project directory that points to the Linux JRE you want bundled with the deployment.

Also, the UI project (`navigate-ui`) must be in a sibling folder and be already built. See its README.md for instructions on how to build it.

To deploy, run in `sbt`:

```
app_navigate_server/Universal/packageZipTarball
```
