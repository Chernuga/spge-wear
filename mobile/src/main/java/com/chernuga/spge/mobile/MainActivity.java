package com.chernuga.spge.mobile;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.chernuga.spge.shared.Lesson;

import java.util.List;

/**
 * Hosts the schedule web app and bridges its parsed data to the watch.
 *
 * <p>The web app keeps its state in {@code localStorage}, so DOM storage must
 * be explicitly enabled here — it is off by default and the page silently
 * fails to restore a previously imported schedule without it.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SpgeMobile";

    /** The deployed schedule app. Loaded over HTTPS so it is a secure origin. */
    private static final String START_URL = "https://spgeparse.netlify.app/";

    private WebView webView;
    private TextView status;
    private Button syncButton;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Built in code rather than inflated from XML so the module has no
        // resource-layout dependency to keep in sync.
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.LinearLayout bar = new android.widget.LinearLayout(this);
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        bar.setPadding(24, 24, 24, 24);
        bar.setBackgroundColor(0xFF10131A);

        syncButton = new Button(this);
        syncButton.setText("Send to watch");
        bar.addView(syncButton);

        status = new TextView(this);
        status.setTextColor(0xFFB9C2D0);
        status.setPadding(24, 0, 0, 0);
        bar.addView(status);

        root.addView(bar, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        root.addView(webView, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        configureWebView();

        syncButton.setOnClickListener(v -> pushToWatch());

        webView.loadUrl(START_URL);
        setStatus("Loading schedule…");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = webView.getSettings();

        // The web app is an ES-module bundle; without JS it renders nothing.
        s.setJavaScriptEnabled(true);

        // localStorage is where the web app caches the imported report. Off by
        // default — the page appears to "forget" data without it.
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        // The app persists its theme/glass preferences in localStorage and
        // re-reads them on start, so the cache must not be treated as ephemeral.
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // The page fetches PDFs from arbitrary school hosts and calls its own
        // /api function; allow those cross-origin XHR/fetch requests.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Zoom is irrelevant in the native shell; keep the layout stable.
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Keep all navigation inside the WebView so the page's own
                // history and localStorage stay intact.
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                setStatus("Page ready");
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(@NonNull ConsoleMessage m) {
                Log.d(TAG, "console: " + m.message());
                return true;
            }
        });
    }

    /**
     * Scrapes the parsed schedule out of the loaded page and pushes it to the
     * watch.
     *
     * <p>Two sources are tried, in order of reliability:
     *
     * <ol>
     *   <li>{@code localStorage["schedule_viewer_data"].allLessons} — the web
     *       app's own structured store, written by {@code saveToStorage()}.
     *       This is the authoritative form: real numbers, exact field names,
     *       no presentation layer in between.</li>
     *   <li>The rendered {@code .lesson-card} elements — scraped from the DOM
     *       when the structured store is missing or empty. This reads what the
     *       user actually sees, so it works even if the storage schema drifts,
     *       at the cost of depending on the card markup.</li>
     * </ol>
     *
     * <p>Both run in the page and return plain JSON, which is parsed natively.
     */
    private void pushToWatch() {
        setStatus("Scraping schedule…");
        webView.evaluateJavascript(DOM_SCRAPE_JS, value -> {
            String json = decodeJsString(value);
            if (json == null || json.trim().isEmpty() || "null".equals(json)) {
                setStatus("Nothing found — import a schedule in the page first");
                Toast.makeText(this, "No schedule found in the page", Toast.LENGTH_LONG).show();
                return;
            }

            List<Lesson> lessons;
            String source;
            try {
                org.json.JSONObject result = new org.json.JSONObject(json);
                lessons = Lesson.listFromJson(result.optJSONArray("lessons"));
                source = result.optString("source", "?");
            } catch (org.json.JSONException e) {
                setStatus("Scrape failed: " + e.getMessage());
                return;
            }

            if (lessons.isEmpty()) {
                setStatus("Found 0 lessons (source: " + source + ")");
                return;
            }

            setStatus("Sending " + lessons.size() + " lessons…");
            final String usedSource = source;
            SyncSender.send(this, lessons, new SyncSender.Callback() {
                @Override
                public void onSent(int nodeCount) {
                    setStatus("Sent " + lessons.size() + " lessons (" + usedSource + ")");
                }

                @Override
                public void onNoNodes() {
                    setStatus("No watch connected");
                    Toast.makeText(MainActivity.this,
                            "No paired watch found. Pair via Galaxy Wearable, then retry.",
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String message) {
                    setStatus("Send failed: " + message);
                }
            });
        });
    }

    /**
     * Runs inside the page. Returns {@code {source, lessons:[...]}}.
     *
     * <p>Kept as a single string constant so the whole scrape is one round trip
     * across the JS bridge, which is slow and must not be called in a loop.
     */
    private static final String DOM_SCRAPE_JS =
            "(function(){"
          + "  function num(v){ var n = parseInt(v, 10); return isNaN(n) ? null : n; }"

            // ---- Source 1: the app's structured lesson store ----
          + "  try {"
          + "    var raw = localStorage.getItem('schedule_viewer_data');"
          + "    if (raw) {"
          + "      var d = JSON.parse(raw);"
          + "      if (d && d.allLessons && d.allLessons.length) {"
          + "        var out = [];"
          + "        for (var i = 0; i < d.allLessons.length; i++) {"
          + "          var l = d.allLessons[i];"
          + "          if (l.start == null || l.end == null || l.day == null) continue;"
          + "          if (!l.subject || !l.className) continue;"
          + "          out.push({"
          + "            start: l.start, end: l.end, day: l.day,"
          + "            subject: String(l.subject),"
          + "            room: String(l.room || '?'),"
          + "            className: String(l.className),"
          + "            teacher: String(l.teacher || ''),"
          + "            color: String(l.color || '#888888'),"
          + "            week: String(l.week || 'C'),"
          + "            group: (l.group == null ? 0 : l.group)"
          + "          });"
          + "        }"
          + "        if (out.length) {"
          + "          return JSON.stringify({source: 'storage', lessons: out});"
          + "        }"
          + "      }"
          + "    }"
          + "  } catch (e) {}"

            // ---- Source 2: scrape the rendered cards ----
            // The grid markup is: .schedule-grid > .day-column > .day-header
            // followed by .period-grid > .period-cell > .lesson-card, where a
            // card holds .lesson-subject, .lesson-time ("[8:45 - 9:30]") and
            // .lesson-details spans ("🏫 8а", "👤 Teacher", "🚪 101").
          + "  try {"
          + "    var cards = document.querySelectorAll('.schedule-section .lesson-card');"
          + "    if (!cards.length) return '';"
          + "    var out2 = [];"
          + "    for (var c = 0; c < cards.length; c++) {"
          + "      var card = cards[c];"
          + "      if (card.classList.contains('filler-card')) continue;"
          + "      var subjEl = card.querySelector('.lesson-subject');"
          + "      var timeEl = card.querySelector('.lesson-time');"
          + "      if (!subjEl) continue;"

            // Which day column does this card sit in?
          + "      var day = null;"
          + "      var col = card.closest('.day-column');"
          + "      if (col) {"
          + "        var hdr = col.querySelector('.day-header');"
          + "        if (hdr) {"
          + "          var names = ['Monday','Tuesday','Wednesday','Thursday','Friday'];"
          + "          var dn = hdr.textContent.trim();"
          + "          for (var k = 0; k < names.length; k++) {"
          + "            if (dn === names[k]) { day = k; break; }"
          + "          }"
          + "        }"
          + "      }"
          + "      if (day == null) {"
          + "        var rows = card.closest('.compare-day-row');"
          + "        if (rows) {"
          + "          var h2 = rows.querySelector('.compare-day-header');"
          + "          if (h2) {"
          + "            var nm = ['Monday','Tuesday','Wednesday','Thursday','Friday'];"
          + "            var t2 = h2.textContent.trim();"
          + "            for (var k2 = 0; k2 < nm.length; k2++) {"
          + "              if (t2 === nm[k2]) { day = k2; break; }"
          + "            }"
          + "          }"
          + "        }"
          + "      }"
          + "      if (day == null) continue;"

            // "[8:45 - 9:30]" -> start/end period indices
          + "      var startIdx = null, endIdx = null;"
          + "      if (timeEl) {"
          + "        var m = timeEl.textContent.match(/(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})/);"
          + "        if (m) {"
          + "          var st = {0:'8:00',1:'8:45',2:'9:50',3:'10:35',4:'11:40',5:'12:25',6:'13:30',7:'14:15'};"
          + "          var en = {1:'8:45',2:'9:30',3:'10:35',4:'11:20',5:'12:25',6:'13:10',7:'14:15',8:'15:00'};"
          + "          for (var p in st) { if (st[p] === m[1]) { startIdx = num(p); break; } }"
          + "          for (var q in en) { if (en[q] === m[2]) { endIdx = num(q); break; } }"
          + "        }"
          + "      }"
          + "      if (startIdx == null || endIdx == null) continue;"

            // Details spans carry the emoji-prefixed fields.
          + "      var spans = card.querySelectorAll('.lesson-details span');"
          + "      var cls = '', teacher = '', room = '';"
          + "      for (var s = 0; s < spans.length; s++) {"
          + "        var txt = spans[s].textContent.replace(/[^\\x00-\\x7F\\u0400-\\u04FF0-9 ]/g, '').trim();"
          + "        var full = spans[s].textContent;"
          + "        if (full.indexOf('\\uD83C\\uDFEB') !== -1) cls = txt;"
          + "        else if (full.indexOf('\\uD83D\\uDC64') !== -1) teacher = txt;"
          + "        else if (full.indexOf('\\uD83D\\uDEAA') !== -1) room = txt;"
          + "      }"
          + "      if (!cls) continue;"

          + "      var bg = card.style.borderColor || '#888888';"
          + "      out2.push({"
          + "        start: startIdx, end: endIdx, day: day,"
          + "        subject: subjEl.textContent.trim(),"
          + "        room: room || '?', className: cls, teacher: teacher,"
          + "        color: bg, week: 'C', group: 0"
          + "      });"
          + "    }"
          + "    if (out2.length) return JSON.stringify({source: 'dom', lessons: out2});"
          + "  } catch (e) {}"
          + "  return '';"
          + "})();";

    /**
     * {@code evaluateJavascript} returns a JSON-encoded value, so a returned
     * string arrives wrapped in quotes with escapes. Unwrap it.
     */
    private static String decodeJsString(String raw) {
        if (raw == null || "null".equals(raw)) return null;
        try {
            return new org.json.JSONArray("[" + raw + "]").getString(0);
        } catch (Exception e) {
            return raw;
        }
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
