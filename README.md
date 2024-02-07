# navigate

New telescope control tool for the Gemini Observatory

# How to package for deployment

The UI project (`navigate-ui`) must be in a sibling folder and be already built. See its README.md for instructions on how to build it.

To build `navigate` Docker image in your local installation, run in `sbt`:

```
deploy_navigate_server/docker:publishLocal
```
