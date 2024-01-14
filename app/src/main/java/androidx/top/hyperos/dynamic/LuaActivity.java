package androidx.top.hyperos.dynamic;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.top.hyperos.dynamic.ext.Config;
import androidx.top.hyperos.dynamic.ext.Tools;
import androidx.top.hyperos.dynamic.hook.XpConfig;
import androidx.top.hyperos.dynamic.script.Base;
import androidx.top.hyperos.dynamic.script.LuaConfig;
import androidx.top.hyperos.dynamic.script.LuaContext;
import androidx.top.hyperos.dynamic.script.LuaLayout;
import androidx.top.hyperos.dynamic.script.function.loadlayout;
import androidx.top.hyperos.dynamic.script.function.print;
import androidx.top.hyperos.dynamic.util.StatusBarUtil;
import androidx.top.hyperos.dynamic.util.ZipUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;

import luaj.Globals;
import luaj.LuaTable;
import luaj.LuaValue;
import luaj.lib.ResourceFinder;
import luaj.lib.jse.JavaPackage;
import luaj.lib.jse.JsePlatform;

public class LuaActivity extends AppCompatActivity implements ResourceFinder, LuaContext {
    public static volatile Context context;
    public static volatile LuaActivity instance;
    public Globals globals;
    public String luaDir;
    public String luaMain = Tools.concat(LuaConfig.luaDir, "main.lua");
    public String luaInit = Tools.concat(LuaConfig.luaDir, "init.lua");
    public String activityName = "main";
    public LinearLayout luaLayout;
    public Toolbar actionBar;
    private static HashMap<String, LuaActivity> activityMap = new HashMap<>();
    public static Handler luaHandler;
    public TextView luaOutputText;
    private ScrollView errorLayout;
    private int mWidth;
    private int mHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this.getContext();
        instance = this.getInstance();
        luaHandler = new MainHandler(this);
        setContentView(R.layout.layout_main);
        luaLayout = findViewById(R.id.layout);
        requestPermission();
        try {
            init();
            initSize();
            initToolbar();
            initErrorLayout();
            initGlobals();
            initENV();
            callFunc("onCreate");
        } catch (Exception e) {
            sendMsg(e.toString());
        }
    }

    private void init() {
        Uri data = getIntent().getData();
        File luas = new File(LuaConfig.luaDir);
        luaDir = luas.getAbsolutePath();
        if (data != null) {
            String path = data.getPath();
            if (!TextUtils.isEmpty(path)) {
                File dir = new File(path);
                if (dir.isFile()) {
                    path = dir.getParent();
                    luaMain = dir.getAbsolutePath();
                }
                luaDir = path;
                setTitle(dir.getName());
            }
        }
        luaDir = checkLuaDir(new File(luaDir)).getAbsolutePath();
        initActivityName();
    }

    public void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, Config.REQUEST_MANAGE_FILES_ACCESS);
            } else {
                // 可直接执行文件相关操作
                File file = new File(Config.AppDir);
                if (!file.exists()) {
                    file.mkdirs();
                }
            }
        }
    }

    private void initGlobals() {
        globals = JsePlatform.standardGlobals();
        globals.finder = this;
        globals.load(new Base());
        globals.jset("this", this);
        globals.jset("activity", this);
        globals.jset("instance", getInstance());
        globals.set("width", getWidth());
        globals.set("height", getHeight());
        globals.set("print", new print(getGlobals()));
        globals.set("loadlayout", new loadlayout(this));
    }

    private void initActivityName() {
        activityName = new File(luaMain).getName();
        int idx = activityName.lastIndexOf(".");
        if (idx > 0) {
            activityName = activityName.substring(0, idx);
            activityMap.put(activityName, this);
        }
    }

    protected void initENV() {
        try {
            LuaValue env = new LuaTable();
            globals.loadfile(luaInit, env).call();
            // load label
            for (String key : LuaConfig.luaLabels) {
                LuaValue label = env.get(key);
                if (label.isstring()) {
                    setTitle(label.tojstring());
                }
            }
            //load theme
            for (String key : LuaConfig.luaThemes) {
                LuaValue theme = env.get(key);
                if (theme.isint()) {
                    setTheme(theme.toint());
                }
                if (theme.isstring()) {
                    setTheme(android.R.style.class.getField(theme.tojstring()).getInt(null));
                }
            }
            //load version
            LuaValue version = env.get(LuaConfig.luaVersion);
            if (version.isint()) {
                int ver = version.toint();
                if (ver <= Config.luaVerSion) {
                    showDialog(R.string.lua_version_alert);
                } else {
                    doFile(getLuaFile());
                }
            }
            //load mod
            for (String key : LuaConfig.luaModules) {
                LuaValue mod = env.get(key);
                if (mod.istable()) {
                    LuaTable MODS = mod.checktable();
                    for (int i = 0; i < MODS.length(); i++) {
                        LuaValue modName = MODS.get(i + 1);
                        if (modName.isstring()) {
                            String str = modName.tojstring();
                            globals.set(str, new JavaPackage(str));
                        }
                    }
                }
            }
        } catch (Exception e) {
            sendMsg(e.toString());
        }
    }

    public void setContentView(LuaTable view) {
        setContentView(new LuaLayout(this).load(view).touserdata(View.class));
    }

    public void setLuaLayout(LuaTable view) {
        this.luaLayout.addView(new LuaLayout(this).load(view).touserdata(View.class));
    }

    public void setLuaLayout(View view) {
        this.luaLayout.addView(view);
    }

    private void initToolbar() {
        actionBar = findViewById(R.id.toolbar);
        setSupportActionBar(actionBar);
        if (actionBar != null) {
            actionBar.setSubtitle(isModuleActivated() ? R.string.xposed_activated : R.string.xposed_unactivated);
            StatusBarUtil.with(this)
                    .setTransparentStatusBar()
                    .setStatusBarTextColor(getResources().getBoolean(R.bool.status_bar_mode_night_no));
        }
    }

    public Toolbar getToolbar() {
        return this.actionBar;
    }

    private void initSize() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(outMetrics);
        this.mWidth = outMetrics.widthPixels;
        this.mHeight = outMetrics.heightPixels;
    }

    private void initErrorLayout() {
        this.errorLayout = new ScrollView(this);
        this.errorLayout.setFillViewport(true);
        this.luaOutputText = new TextView(this);
        this.luaOutputText.setText("");
        this.luaOutputText.setTextIsSelectable(true);
        this.errorLayout.addView(this.luaOutputText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private File checkLuaDir(File dir) {
        if (dir == null) {
            return new File(this.luaDir);
        }
        if (new File(dir, this.luaMain).exists() && new File(dir, this.luaInit).exists()) {
            return dir;
        }
        return checkLuaDir(dir.getParentFile());
    }

    public Object callFunc(String name, Object... args) {
        try {
            LuaValue fun = globals.get(name);
            if (fun.isfunction()) {
                return fun.jcall(args);
            }
        } catch (Exception e) {
        }
        return null;
    }

    public void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, Config.READ_REQUEST_CODE);
    }

    public void showLauncherIcon(boolean isShow) {
        PackageManager packageManager = this.getPackageManager();
        int show = isShow ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setComponentEnabledSetting(getAliseComponentName(), show, PackageManager.DONT_KILL_APP);
    }

    private ComponentName getAliseComponentName() {
        return new ComponentName(LuaActivity.this, BaseActivity.class);
    }

    private boolean isModuleActivated() {
        return false;
    }

    public boolean checkModule() {
        return isModuleActivated();
    }

    private void importProject(Context context, Uri file) {
        Dialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_tip)
                .setMessage(R.string.tip_import_project)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String path = Config.AppDir;
                        ZipUtil.unzipAndShowDialog(context, file, path);
                        Toast.makeText(context, Tools.getString(R.string.app_save_success), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
    }

    private void showDialog(String text) {
        new AlertDialog.Builder(getContext())
                .setMessage(text)
                .show();
    }

    public LuaActivity getInstance() {
        if (instance == null) {
            instance = new LuaActivity();
        }
        return instance;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        callFunc("onOptionsItemSelected", item);
        int id = item.getItemId();
        switch (id) {
            case Config.MENU_IMPORT:
                performFileSearch();
                return true;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    public Globals getGlobals() {
        return this.globals;
    }

    @Override
    public String getLuaFile() {
        return this.luaMain;
    }

    @Override
    public String getLuaFile(String filename) {
        return new File(getLuaDir(), filename).getAbsolutePath();
    }

    @Override
    public String getLuaDir() {
        return this.luaDir;
    }

    @Override
    public Object doFile(String path, Object... args) {
        return this.globals.loadfile(path).jcall(args);
    }

    @Override
    public int getHeight() {
        return this.mHeight;
    }

    @Override
    public int getWidth() {
        return this.mWidth;
    }

    @Override
    public void call(String func, Object... args) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                globals.get(func).jcall(args);
            }
        });
    }

    @Override
    public void set(String name, Object value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                globals.jset(name, value);
            }
        });
    }

    @Override
    public InputStream findResource(String filename) {
        try {
            if (new File(filename).exists()) {
                return new FileInputStream(filename);
            }
            return new FileInputStream(new File(getLuaFile(filename)));
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public String findFile(String filename) {
        if (filename.startsWith("/")) {
            return filename;
        }
        return getLuaFile();
    }

    @Override
    protected void onStart() {
        super.onStart();
        callFunc("onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        callFunc("onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        callFunc("onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        callFunc("onStop");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        callFunc("onNewIntent", intent);
        super.onNewIntent(intent);
    }

    @Override
    protected void onDestroy() {
        try {
            callFunc("onDestroy");
            System.gc();
        } catch (Exception e) {
            sendMsg("onDestroy" + e.getMessage());
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Config.REQUEST_MANAGE_FILES_ACCESS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // 这里执行创建文件夹操作
                    File file = new File(LuaConfig.luaDir);
                    if (!file.exists()) {
                        file.mkdirs();
                    }
                    File file2 = new File(XpConfig.luaDir);
                    if (!file2.exists()) {
                        file2.mkdirs();
                    }
                } else {
                    showDialog(R.string.tip_not_permission);
                }
            }
        }
        if (requestCode == Config.READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (Environment.isExternalStorageManager()) {
                Uri uri = data.getData();
                importProject(this, uri);
            }
        }
        callFunc("onActivityResult", requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, Config.MENU_IMPORT, 0, "导入");
        callFunc("onCreateOptionsMenu", menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        callFunc("onCreateContextMenu", menu, v, menuInfo);
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        callFunc("onContextItemSelected", item);
        return super.onContextItemSelected(item);
    }

    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public static void sendMsg(String msg) {
        seed(msg, 0);
    }

    public static void sendError(String msg) {
        seed(msg, 1);
    }

    private static void seed(String msg, int i) {
        Message message = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("data", msg);
        message.setData(bundle);
        message.what = i;
        luaHandler.sendMessage(message);
    }

    private static class MainHandler extends Handler {
        WeakReference<Activity> activityWeakReference;

        private MainHandler(Activity activity) {
            activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Activity activity = activityWeakReference.get();
            if (activity == null || !(activity instanceof LuaActivity)) {
                return;
            }
            LuaActivity luaActivity = (LuaActivity) activity;
            switch (msg.what) {
                case 0: {
                    String data = msg.getData().getString("data");
                    luaActivity.toast(data);
                    try {
                        luaActivity.luaOutputText.append(data + "\n");
                        luaActivity.luaLayout.addView(luaActivity.errorLayout);
                    } catch (Exception e) {
                    }
                }
                break;
                case 1: {
                    String data = msg.getData().getString("data");
                    try {
                        luaActivity.luaOutputText.append(data + "\n");
                        luaActivity.luaLayout.addView(luaActivity.errorLayout);
                    } catch (Exception e) {
                    }
                }
            }
        }
    }
}
