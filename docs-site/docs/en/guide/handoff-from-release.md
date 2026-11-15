# Import Data from the Stable App

The beta build and the stable build of AiVerse are two separate apps signed with the same key, and each keeps its own data: model configuration, skills, chat history and so on are never shared automatically. Once you install the beta build, you do not have to set everything up from scratch — you can bring over the configuration you already use in the stable build.

## What Gets Imported

- Skills
- MCP server configuration
- Roles (AI personas)
- Tool permission rules
- App settings (theme, appearance, language, preferences)
- AI providers and model lists
- Git credentials

::: tip Importing merges, it does not wipe
Importing never deletes what the beta build already has. Entries that already exist in the beta build are replaced by the stable build's copy; everything else stays exactly as it is.
:::

## What Is Not Migrated

The container runtime (Node.js, Python, Git and other base tools) does not come along with the data — the beta build initialises its own container from scratch. When you open the terminal for the first time, still wait for the container to finish preparing, and reinstall any third-party tools you had set up inside the container (a Flutter SDK, Playwright browsers, and so on).

::: warning Chat history is not part of the import
This feature only carries over configuration-style data, not chat sessions and messages or workspace files. To keep your chats across builds, use [Backup & restore](https://aivapp.cn/md/en/guide/backup.md) instead.
:::

::: warning The beta build is not meant for daily work
Beta builds are produced on a daily schedule, carry a watermark on the app screens, are not guaranteed to be stable, and are excluded from in-app update checks. For real work, stick with the stable or preview build (see [Choosing a Version](https://aivapp.cn/md/en/guide/quick-start.md) in the Quick Start).
:::

## Steps

1. **First launch of the beta build**: after you accept the user agreement, an "Import data from the stable app" card appears on the home page.
2. **Tap "Import"**: the system opens the stable build of AiVerse. If the stable build is not installed, you get a message saying it cannot be found — install it first and try again.
3. **Approve in the stable build**: the stable build shows an authorization screen that explains what is being shared and the risks involved, and lists the data item by item.
4. **Tap "Allow"**: the data is transferred to the beta build and imported automatically. When it is done, the stable build reports that the authorization has ended, and the beta build shows an import-completed message.
5. **Temporary files are cleaned up automatically**: the stable build deletes the temporary files created for this transfer once the import finishes — nothing to tidy up by hand.

If you do not want to import right now, tap "Later" or close the card. The beta build still works normally without imported data, and the card comes back the next time it starts.

## Choosing a Channel

The authorization screen lets you pick which channel the transfer uses. Both channels import the same data; they differ in reliability:

| Channel | Characteristics | When to use |
| --- | --- | --- |
| **Recommended** (selected by default) | The data never leaves the stable build's private storage; a one-time read access is granted just for this import and revoked right after, so no other app can reach it | The default choice in almost every case |
| **Fallback** | The stable build opens a temporary connection that only works inside your own device, and closes it again as soon as the data has been read | If the recommended channel fails to read the data, switch here and retry |

::: tip No extra permissions needed
Neither channel requires granting storage access, and neither needs the internet — everything happens on your own device.
:::

## Troubleshooting

**Nothing happens after tapping "Import", or you are told the stable build cannot be found**

Make sure the stable build is installed and opens normally; the beta build and the stable build must live on the same device.

**The stable build does not show the authorization screen**

The request may have been intercepted by the system, or the stable build was pushed to the background. Bring the stable build back to the foreground and the authorization screen will appear again; if it still does not, go back to the beta build and tap "Import" on the card once more.

**The data list on the authorization screen looks wrong**

That list is exactly what will be sent once you tap "Allow" — check every entry before approving.

**The import fails partway through**

The beta build shows a failure message. Start over from the card by tapping "Import" again, or retry through the other channel.

**Something is still missing after the import**

Compare against "What Gets Imported" and "What Is Not Migrated" above: chat history, workspace files, and the runtime environment installed inside the container are all outside the scope of this import.