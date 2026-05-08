package dev.isaacudy.udeploy.launcher

import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_create
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_destroy
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_hint_t
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_run
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_set_html
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_set_size
import dev.isaacudy.udeploy.launcher.cinterop.webview.webview_set_title
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual fun runLauncher(args: Array<String>) {
    // Open a small debug window so the launcher has a visible surface
    // during development. The window's content is hardcoded HTML for
    // now; real status / progress wiring lands once we have manifest
    // fetching and payload swap implemented.
    val w = webview_create(/* debug = */ 1, /* window = */ null)
        ?: error("webview_create returned null")
    try {
        webview_set_title(w, "udeploy launcher (debug)")
        webview_set_size(w, 600, 400, webview_hint_t.WEBVIEW_HINT_NONE)
        webview_set_html(
            w,
            """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8">
                <style>
                  body {
                    font-family: -apple-system, system-ui, sans-serif;
                    margin: 24px;
                    color: #1a1a1a;
                    background: #faf6ee;
                  }
                  h1 { font-size: 18px; margin: 0 0 12px 0; }
                  .kicker { font-size: 11px; color: #888; letter-spacing: 0.1em; text-transform: uppercase; margin-bottom: 4px; }
                  pre { background: #f0e8d4; padding: 12px; border-radius: 6px; font-size: 12px; }
                </style>
              </head>
              <body>
                <div class="kicker">udeploy / debug</div>
                <h1>Launcher running</h1>
                <p>This window will eventually show update status,
                   download progress, and error states.</p>
                <pre>args: ${args.joinToString(" ")}</pre>
              </body>
            </html>
            """.trimIndent(),
        )
        webview_run(w)
    } finally {
        webview_destroy(w)
    }
}
