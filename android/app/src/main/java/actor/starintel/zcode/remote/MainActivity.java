package actor.starintel.zcode.remote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Native connection management; unprivileged upstream web UI owns every coding operation. */
public final class MainActivity extends Activity {
    private static final int PICK_FILE = 100;
    private static final int BACKGROUND = 0xff121018;
    private static final int TEXT = 0xfff2edf8;
    private final SessionState state = new SessionState();
    private final List<Profile> profiles = new ArrayList<>();
    private VaultActor vault;
    private LinearLayout root;
    private TextView status;
    private WebView web;
    private Profile active;
    private ValueCallback<Uri[]> upload;
    private boolean vaultBusy;
    private boolean vaultFailed;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        WebView.setWebContentsDebuggingEnabled(false);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::requestLeave);
        }
        vault = new VaultActor(getApplicationContext());
        showDesktops();
        vaultBusy = true;
        vault.load(this::loaded);
        // Never restore a token-bearing URL or a browser history from an Android Bundle.
    }
    private void loaded(List<Profile> items, boolean failed) {
        vaultBusy = false;
        vaultFailed = failed;
        if (!failed) { profiles.clear(); profiles.addAll(items); }
        if (active == null) showDesktops();
        if (failed) message("Connection storage could not be read or updated. Existing data was not replaced. Restart the app to retry; clear app storage only to reset an unrecoverable vault.");
        consumeSharedLink();
    }
    @Override protected void onNewIntent(Intent incoming) {
        super.onNewIntent(incoming);
        setIntent(incoming);
        if (!vaultBusy) consumeSharedLink();
    }
    private void consumeSharedLink() {
        Intent incoming = getIntent();
        setIntent(new Intent());
        if (Intent.ACTION_SEND.equals(incoming.getAction()) && "text/plain".equals(incoming.getType())) {
            String value = incoming.getStringExtra(Intent.EXTRA_TEXT);
            if (value != null && value.length() <= LinkPolicy.MAX_URL_LENGTH && !vaultFailed) addForm(value);
            else message("Share one complete HTTPS connection link.");
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void newRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.setSaveEnabled(false);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(12) + insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    dp(12) + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
    }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(TEXT);
        view.setTextSize(size);
        view.setPadding(dp(4), dp(8), dp(4), dp(8));
        parent.addView(view);
        return view;
    }
    private Button button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
        return button;
    }
    private void showDesktops() {
        newRoot();
        text(root, "ZCode Remote", 27);
        text(root, "Your desktop runs the code. Your phone controls the session.", 15);
        ScrollView scroll = new ScrollView(this);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(cards);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (profiles.isEmpty()) text(cards, "Open ZCode on your desktop, select the phone icon, then scan or paste its remote-control link.", 17);
        for (Profile profile : profiles) {
            text(cards, profile.name, 21);
            text(cards, profile.modeLabel() + "\n" + profile.link.origin, 14);
            button(cards, "Open " + profile.name, () -> open(profile));
            button(cards, "Forget connection", () -> forget(profile));
        }
        button(root, "Add desktop / paste link", () -> addForm(""));
        button(root, "Scan desktop QR", this::scan);
        text(root, "Desktop attach uses the existing desktop window. Direct web uses a separate web server. Neither runs an agent on Android.", 12);
    }
    private EditText input(LinearLayout form, String hint, String value, int maximum, boolean secret) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setSaveEnabled(false);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_NORMAL));
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maximum)});
        field.setText(value);
        form.addView(field);
        return field;
    }
    private void addForm(String supplied) {
        if (vaultBusy || vaultFailed) { message("Connection storage is not ready."); return; }
        if (profiles.size() >= VaultActor.MAX_PROFILES) { message("Forget a saved connection before adding another (maximum 8)."); return; }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), 0, dp(16), 0);
        form.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        EditText name = input(form, "Desktop name", "Home desktop", 64, false);
        EditText url = input(form, "Complete HTTPS connection link", supplied, LinkPolicy.MAX_URL_LENGTH, true);
        RadioGroup modes = new RadioGroup(this);
        RadioButton attach = new RadioButton(this);
        attach.setId(View.generateViewId());
        attach.setText("Attach to desktop window");
        RadioButton direct = new RadioButton(this);
        direct.setId(View.generateViewId());
        direct.setText("Direct web server (separate sessions)");
        modes.addView(attach); modes.addView(direct); modes.check(attach.getId()); form.addView(modes);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Add desktop").setView(form)
                .setNegativeButton("Cancel", null).setPositiveButton("Review", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                Profile profile = new Profile(UUID.randomUUID().toString(), name.getText().toString(),
                        modes.getCheckedRadioButtonId() == attach.getId() ? Profile.Mode.DESKTOP_ATTACH : Profile.Mode.DIRECT_WEB,
                        url.getText().toString());
                url.setText("");
                dialog.dismiss();
                new AlertDialog.Builder(this).setTitle("Trust this connection?")
                        .setMessage(profile.link.origin + "\n\n" + profile.modeLabel()
                                + "\nOnly approve a link you generated on your desktop. The link grants control. It will be encrypted on this phone.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Save connection", (d, which) -> {
                            vaultBusy = true;
                            vault.add(profile, this::loaded);
                        }).show();
            } catch (IllegalArgumentException error) { url.setError("Enter a valid HTTPS link and desktop name."); }
        }));
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private void scan() {
        if (vaultBusy || vaultFailed) { message("Connection storage is not ready."); return; }
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build();
        GmsBarcodeScanning.getClient(this, options).startScan()
                .addOnSuccessListener(this, barcode -> {
                    String value = barcode.getRawValue();
                    if (value == null || value.length() > LinkPolicy.MAX_URL_LENGTH) message("Invalid connection QR.");
                    else addForm(value);
                }).addOnFailureListener(this, error -> message("QR scanner unavailable. Paste or share the desktop link instead."));
    }
    private void forget(Profile profile) {
        if (vaultBusy) return;
        new AlertDialog.Builder(this).setTitle("Forget " + profile.name + "?")
                .setMessage("This removes the phone's saved link. To revoke remote control, use Stop or refresh the QR in desktop ZCode.")
                .setNegativeButton("Cancel", null).setPositiveButton("Forget", (d, which) -> {
                    vaultBusy = true;
                    vault.remove(profile.id, this::loaded);
                }).show();
    }
    private void open(Profile profile) {
        if (active != null) return;
        active = profile;
        startPage();
    }
    @SuppressLint("SetJavaScriptEnabled") // Required by upstream React UI; no native JS bridge is installed.
    private void startPage() {
        destroyPage();
        long generation = state.begin();
        newRoot();
        text(root, active.name + " · " + active.modeLabel(), 17);
        text(root, active.link.origin, 12);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(actions);
        button(actions, "Desktops", this::requestLeave);
        button(actions, "Reload", this::requestReload);
        button(actions, "Details", this::diagnostics);
        status = text(root, "Loading page. Desktop task status is not yet known.", 12);
        web = new WebView(this);
        final WebView current = web;
        web.setSaveEnabled(false);
        web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(true); // New windows denied by WebChromeClient's default.
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setDownloadListener((url, agent, disposition, mime, length) -> message("Downloads are not supported in this build."));
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (upload != null) upload.onReceiveValue(null);
                upload = callback;
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                try { startActivityForResult(picker, PICK_FILE); }
                catch (android.content.ActivityNotFoundException error) { upload.onReceiveValue(null); upload = null; }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (active != null && LinkPolicy.sameOrigin(active.link, request.getUrl().toString())) return false;
                message("Navigation outside the approved HTTPS origin was blocked. Sign in and configure providers on the desktop.");
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (active == null || !LinkPolicy.sameOrigin(active.link, url)) {
                    view.stopLoading();
                    fail(generation, "Navigation outside the approved origin was stopped.");
                }
            }
            @Override public void onPageFinished(WebView view, String url) {
                state.ready(generation);
                if (state.accepts(generation) && state.phase() == SessionState.Phase.PAGE_READY) {
                    status.setText("Page loaded. Use ZCode's own status below to confirm the desktop connection.");
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) fail(generation, "Page could not load. Check the desktop and network; nothing was automatically resent.");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) fail(generation, "Server rejected the page request. The link may have expired. Check desktop ZCode.");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                fail(generation, "TLS certificate rejected. Use a trusted HTTPS endpoint; no bypass is available.");
            }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                fail(generation, "Web renderer stopped. Reload to inspect task state before resending anything.");
                if (view == web) destroyPage();
                return true;
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        // A fresh auth context per attach/reload; no cross-profile browser credentials.
        WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().removeAllCookies(ignored -> {
            if (web == current && active != null && state.accepts(generation)) current.loadUrl(active.link.url);
        });
    }
    private void fail(long generation, String message) {
        state.fail(generation);
        if (state.accepts(generation)) status.setText(message);
    }
    private void requestReload() {
        if (active == null) return;
        new AlertDialog.Builder(this).setTitle("Reload the remote view?")
                .setMessage("Unsent text may be lost. If a send was interrupted, inspect the task before sending it again. This app does not replay instructions.")
                .setNegativeButton("Cancel", null).setPositiveButton("Reload", (d, which) -> startPage()).show();
    }
    private void requestLeave() {
        if (active == null) { finish(); return; }
        new AlertDialog.Builder(this).setTitle("Close phone view?")
                .setMessage("This does not cancel desktop work or revoke the link. Unsent text may be lost.")
                .setNegativeButton("Stay", null).setPositiveButton("Close view", (d, which) -> {
                    state.close(); destroyPage(); active = null; clearWebData(); showDesktops();
                }).show();
    }
    private void diagnostics() {
        if (active == null) return;
        // Deliberately omit profile name, paths, query strings, fragments, page titles and exceptions.
        String report = "ZCODE-REMOTE-DIAGNOSTICS/1\napp=0.1.0\nandroid_api=" + Build.VERSION.SDK_INT
                + "\nmode=" + active.mode.name() + "\npage_phase=" + state.phase().name()
                + "\napproved_origin=" + active.link.origin
                + "\nagent_connection=unobserved\nupstream_version=unverified\nauto_replay=false";
        new AlertDialog.Builder(this).setTitle("Connection details").setMessage(report)
                .setNegativeButton("Close", null).setPositiveButton("Share diagnostics", (d, which) ->
                        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, report), "Share redacted diagnostics"))).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_FILE && upload != null) {
            Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
            upload.onReceiveValue(uri != null && "content".equals(uri.getScheme()) ? new Uri[]{uri} : null);
            upload = null;
        }
    }
    private void destroyPage() {
        if (upload != null) { upload.onReceiveValue(null); upload = null; }
        if (web == null) return;
        WebView old = web; web = null;
        old.stopLoading();
        if (old.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) old.getParent()).removeView(old);
        old.setWebChromeClient(null);
        old.setWebViewClient(new WebViewClient());
        old.clearHistory(); old.clearCache(true); old.destroy();
    }
    private void clearWebData() {
        WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().removeAllCookies(null);
    }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { requestLeave(); }
    @Override protected void onDestroy() {
        state.close(); destroyPage(); clearWebData();
        if (vault != null) vault.close();
        super.onDestroy();
    }
}
