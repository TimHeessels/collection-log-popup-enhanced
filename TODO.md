Early next week we will be deploying API changes required for Jagex's max cash update on Sep 30. These API updates are currently in the latest snapshot. If you have a plugin which depends on item prices or the GE, we recommend testing your plugin against the latest version by setting:

def runeLiteVersion = 'latest.integration'


in build.gradle and running your plugin. The client should report as version 1.13.0-SNAPSHOT.

We're also taking this time to make a few changes to how @PluginDependency works, that is part of a larger effort for us to sandbox network access. If your plugin uses @PluginDependency it may require changes.

Finally, we're introducing an API change to script events which send packets to the server that requires an opt in.
If you have a plugin which manually runs script events (such as op script events),  you may see an "Packet sent from script without permission" error without it.

If you have changes to make for your plugin that require the new API, open a pull request to the pluginhub targeting the 1.13 branch. You may also be able to target master, instead, if the changes are backwards compatible.

I'm making a thread off of this message for if you need assistance with API updates. @pluginhub-contributor 



