# Disposable App control fixture

This data-free test application is separate from the headless production agent.
Its single visible Activity lets an administrator verify a launch and force-stop;
it requests CAMERA so permission policy can be verified without touching payment
applications. It is not part of the deployed SCANPLUS agent and must be uninstalled
after the test workflow. The release agent lives in `scanplus-agent` and has no
Activity or launcher icon.
