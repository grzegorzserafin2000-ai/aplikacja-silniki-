package pl.cg.ziehlconfig;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.widget.ImageView;
import android.widget.Toast;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CLIMA GOLD – ZIEHL-ABEGG motor configurator, test version.
 * Reads MODBUS registers non-destructively. Auto-identifies supported ECblue
 * controller codes; writing remains locked for unverified device families.
 */
public final class MainActivity extends Activity {
    private static final String USB_ACTION = "pl.cg.ziehlconfig.wizard.USB_PERMISSION";
    private static final int NAVY = Color.rgb(0, 0, 0);
    private static final int BLUE = Color.rgb(245, 105, 22);
    private static final int LIGHT = Color.rgb(0, 0, 0);
    private static final int GREY = Color.rgb(198, 208, 218);
    private static final int GREEN = Color.rgb(111, 214, 159);
    private static final int CARD = Color.rgb(22, 22, 22);
    private static final int FIELD = Color.rgb(37, 37, 37);
    private static final int BORDER = Color.rgb(73, 73, 73);
    private static final int WHITE = Color.rgb(247, 248, 250);

    private UsbManager usbManager;
    private UsbSerialDriver chosenDriver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService jobs = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private volatile boolean busy = false;
    private volatile boolean pendingStart = false;
    private volatile boolean pendingManual = false;

    private TextView usbStatus, deviceStatus, progress, readResult, info, writeHint;
    private Button menuBackButton, detectButton, stopButton, writeButton, reportButton, manualButton, factoryButton, manualToggleButton;
    private CheckBox extendedScan;
    private LinearLayout busyVisual,codeStages;
    private ImageView activityFan;
    private android.animation.ObjectAnimator fanSpin;
    private volatile String lastScanReport = "Nie uruchomiono jeszcze skanowania.";
    private String guidedAttemptHistory = "";
    private LinearLayout editorPanel, manualFields;
    private ScrollView contentScroll;
    private EditText addressEdit, manualFromAddress, manualToAddress, manualTimeout;
    private Spinner manualBaud, manualMode;
    private Spinner baudSpinner, modeSpinner, purposeSpinner, motorNumberSpinner;
    private LinearLayout manualConfigPanel;
    private volatile int foundProductCode=-1, foundFirmware=-1;
    private volatile boolean compatibleWriteProfile=false;

    private volatile int foundAddress = -1;
    private volatile int rawCom = -1;
    private volatile SerialBridge.Link foundLink;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!USB_ACTION.equals(intent.getAction())) return;
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && pendingStart) {
                pendingStart = false;
                boolean wasManual = pendingManual;
                pendingManual = false;
                if (wasManual) startManualScan();
                else startScan();
            } else {
                pendingStart = false;
                pendingManual = false;
                detectButton.setEnabled(true);
                setInfo(simpleWizard() ? "Nie przyznano dostępu do urządzenia USB. Naciśnij Spróbuj ponownie i zezwól na dostęp."
                    : "Nie przyznano dostępu do urządzenia USB.");
            }
        }
    };

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Flags are supplied on API 33+ below.
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(NAVY);
            getWindow().setNavigationBarColor(NAVY);
        }
        usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter(USB_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver, filter);
        buildScreen();
        updateUsbStatus();
        if(guidedTarget()>0) {
            factoryButton.setVisibility(View.GONE);
            manualToggleButton.setVisibility(View.GONE);
            if (!simpleWizard()) setInfo("Kreator: "+GuidedSession.motorLabel(guidedTarget())+" • cel 9600 / 8N2, CM=3");
            applyGuidedTarget();
        }
        if(getIntent().hasExtra("verify_addresses")) {
            factoryButton.setVisibility(View.GONE);
            manualToggleButton.setVisibility(View.GONE);
            detectButton.setText("SPRAWDŹ WSZYSTKIE SILNIKI");
            setInfo("Sprawdzam wszystkie wentylatory. Test nie zmienia ich ustawień.");
        }
        if (simpleWizard()) {
            if (state != null) {
                guidedAttemptHistory = state.getString("guidedHistory", "");
                lastScanReport = state.getString("guidedReport", lastScanReport);
            }
            buildGuidedScreen();
            // Only the explicit start action from SetupActivity authorizes a new automatic run.
            // Activity recreation never automatically repeats a write.
            if (state == null) handler.post(this::requestScan);
            else guidedFailure("Działanie zostało przerwane. Sprawdź wentylator przed ponowną próbą.");
        }
    }

    private boolean simpleWizard() {
        return getIntent().getBooleanExtra("guided_auto", false) || getIntent().hasExtra("verify_addresses");
    }

    private void move(LinearLayout parent, View child, int margin) {
        if (child.getParent() instanceof ViewGroup) ((ViewGroup) child.getParent()).removeView(child);
        add(parent, child, margin);
    }

    /** Dedicated nontechnical screen, sharing the proven transport and programmer. */
    private void buildGuidedScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.BLACK);
        contentScroll = scroll;
        LinearLayout root = column(); root.setPadding(dp(24), dp(30), dp(24), dp(36));
        scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, i) -> {
            scroll.setPadding(0, i.getSystemWindowInsetTop(), 0, i.getSystemWindowInsetBottom()); return i;
        });
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.climagold_full);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(logo, new LinearLayout.LayoutParams(-1, dp(80)));
        add(root, text(guidedTarget() > 0 ? GuidedSession.simpleLabel(guidedTarget()).toUpperCase(java.util.Locale.ROOT)
            : "SPRAWDZENIE CENTRALI", 30, WHITE, true), 24);
        add(root, text(guidedTarget() > 0 ? "Pozostaw włączony tylko ten wentylator. Centrala musi być zatrzymana."
            : "Pozostaw włączone wszystkie skonfigurowane wentylatory.", 20, WHITE, false), 16);
        move(root, deviceStatus, 24); deviceStatus.setText("Gotowe do rozpoczęcia");
        move(root, busyVisual, 20);
        move(root, codeStages, 16);
        move(root, progress, 14); progress.setText("");
        move(root, info, 14); info.setText("Aplikacja wykona kolejne kroki automatycznie.");
        move(root, detectButton, 24);
        detectButton.setText("Spróbuj ponownie");
        move(root, stopButton, 12);
        move(root, menuBackButton, 16);
        add(root, text("Obracający się symbol oznacza pracę aplikacji, a nie obracanie wentylatora.", 13, GREY, false), 20);
        Visuals.enter(root);
    }

    private void guidedFailure(String message) {
        deviceStatus.setText("Nie udało się potwierdzić ustawień");
        deviceStatus.setTextColor(WHITE);
        progress.setText(message);
        progress.setTextColor(GREY);
        info.setText("Pozostaw włączony tylko wskazany wentylator. Sprawdź połączenie i spróbuj ponownie. Szczegóły zachowano w raporcie.");
        codeStages.setVisibility(View.GONE);
    }

    private int guidedTarget(){return getIntent().getIntExtra("guided_target",-1);}
    private void applyGuidedTarget(){
        if(guidedTarget()<1)return;
        purposeSpinner.setSelection(2);
        addressEdit.setText(String.valueOf(guidedTarget()));
        baudSpinner.setSelection(0);modeSpinner.setSelection(0);
        addressEdit.setEnabled(false);purposeSpinner.setEnabled(false);motorNumberSpinner.setEnabled(false);
        baudSpinner.setEnabled(false);modeSpinner.setEnabled(false);
    }
    private int dp(float v) { return (int)(getResources().getDisplayMetrics().density * v + .5f); }
    private GradientDrawable shape(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius));
        return g;
    }
    private GradientDrawable outlined(int color, float radius) {
        GradientDrawable g=shape(color,radius);
        g.setStroke(dp(1), BORDER);
        return g;
    }
    private TextView text(String content, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content); t.setTextColor(color); t.setTextSize(sp);
        t.setGravity(Gravity.START);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }
    private LinearLayout card() {
        LinearLayout c = column();
        c.setBackground(outlined(CARD, 18));
        c.setPadding(dp(18),dp(20),dp(18),dp(20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(15));
        c.setLayoutParams(lp);
        return c;
    }
    private void add(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.topMargin = dp(topDp);
        parent.addView(child,p);
    }
    private Drawable buttonBackground(int normal) {
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},shape(Color.rgb(42,42,42),13));
        states.addState(new int[]{android.R.attr.state_pressed},shape(Color.rgb(255,188,94),13));
        states.addState(new int[]{android.R.attr.state_focused},shape(Color.rgb(255,188,94),13));
        states.addState(new int[]{},shape(normal,13));
        return new RippleDrawable(ColorStateList.valueOf(Color.rgb(255,220,165)),states,shape(Color.WHITE,13));
    }
    private ColorStateList buttonTextColors() {
        return new ColorStateList(new int[][]{
            new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_pressed},
            new int[]{android.R.attr.state_focused},new int[]{}},
            new int[]{GREY,Color.BLACK,Color.BLACK,WHITE});
    }
    private Button button(String name) {
        Button b = new Button(this);
        b.setText(name); b.setAllCaps(false);
        b.setTextSize(15); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(buttonTextColors()); b.setBackgroundTintList(null); b.setBackground(buttonBackground(BLUE));
        b.setMinHeight(dp(52));
        b.setPadding(dp(12),dp(13),dp(12),dp(13));
        Visuals.decorate(b);
        b.setCompoundDrawablesRelative(Visuals.icon(this,Visuals.actionIcon(name),WHITE,24),null,null,null);
        return b;
    }
    private Button secondaryButton(String name) {
        Button b=button(name);
        b.setBackground(buttonBackground(FIELD));
        b.setTextColor(buttonTextColors());
        return b;
    }
    /** Keep a numeric input above Android's on-screen keyboard.
     * Runs twice since some tablet keyboards resize the window after focus. */
    private void keepInputVisible(EditText field) {
        if (contentScroll == null || field == null || !field.isFocused()) return;
        Rect rect = new Rect();
        field.getDrawingRect(rect);
        contentScroll.offsetDescendantRectToMyCoords(field, rect);
        int viewportBottom = contentScroll.getHeight() - contentScroll.getPaddingBottom() - dp(18);
        int overshoot = rect.bottom + dp(20) - viewportBottom;
        if (overshoot > 0) contentScroll.smoothScrollBy(0, overshoot);
    }
    private void trackKeyboardFocus(EditText field) {
        field.setOnFocusChangeListener((v, focused) -> {
            if (focused && contentScroll != null) {
                contentScroll.postDelayed(() -> keepInputVisible(field), 160);
                contentScroll.postDelayed(() -> keepInputVisible(field), 360);
            }
        });
    }
    private EditText number(int initial) {
        EditText e = new EditText(this);
        e.setText(String.valueOf(initial));
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setTextColor(WHITE); e.setTextSize(18);
        e.setBackground(outlined(FIELD, 11));
        e.setPadding(dp(15),dp(13),dp(15),dp(13));
        e.setSelectAllOnFocus(true);
        trackKeyboardFocus(e);
        return e;
    }
    private Spinner spinner(String... values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView row=text(getItem(position),15,WHITE,false);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(15),dp(13),dp(15),dp(13));
                return row;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView row=text(getItem(position),15,WHITE,false);
                row.setPadding(dp(14),dp(15),dp(14),dp(15));
                row.setBackgroundColor(FIELD);
                return row;
            }
        };
        s.setAdapter(adapter);
        s.setBackground(outlined(FIELD,11));
        s.setMinimumHeight(dp(48));
        return s;
    }
    private void label(LinearLayout root, String value) { add(root, text(value,13,GREY,true),14); }

    // Clima Gold SIMPLE: only detection, choice and one programming button.
    // Technical controls are available in a collapsed panel for maintenance.
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        contentScroll=scroll;
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(LIGHT);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout root=column();
        root.setPadding(dp(16),dp(18),dp(16),dp(42));
        scroll.addView(root);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view,insets)->{
                Insets sys=insets.getInsets(WindowInsets.Type.systemBars());
                Insets ime=insets.getInsets(WindowInsets.Type.ime());
                scroll.setPadding(0,sys.top,0,Math.max(sys.bottom,ime.bottom)+dp(12));
                if(insets.isVisible(WindowInsets.Type.ime())) {
                    View v=getCurrentFocus();
                    if(v instanceof EditText) scroll.post(()->keepInputVisible((EditText)v));
                }
                return insets;
            });
            scroll.requestApplyInsets();
        }
        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(10),dp(10),dp(10),dp(10));
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.climagold_full);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackgroundColor(LIGHT);
        logo.setPadding(dp(8),dp(8),dp(8),dp(8));
        logo.setContentDescription("Clima Gold");
        top.addView(logo,new LinearLayout.LayoutParams(-1,dp(96)));
        add(top,text("ZIEHL-ABEGG  •  Konfigurator",13,BLUE,true),8);
        add(root,top,0);
        boolean inWizard=guidedTarget()>0 || getIntent().hasExtra("verify_addresses");
        menuBackButton=secondaryButton(inWizard?"Wróć do kreatora":"Wróć do menu");
        menuBackButton.setOnClickListener(v->onBackPressed());
        add(root,menuBackButton,8);

        LinearLayout detect=card(); add(root,detect,16);
        add(detect,text("1. Podłącz i wykryj silnik",18,WHITE,true),0);
        usbStatus=text("Sprawdzam kabel USB–RS485...",12,GREY,false);add(detect,usbStatus,8);
        detectButton=button("WYSZUKAJ SILNIK");add(detect,detectButton,12);
        detectButton.setOnClickListener(v->requestScan());
        deviceStatus=text("Nie wykryto silnika",22,WHITE,true);add(detect,deviceStatus,12);
        busyVisual=new LinearLayout(this);busyVisual.setGravity(Gravity.CENTER_VERTICAL);
        activityFan=new ImageView(this);activityFan.setImageResource(R.drawable.ui_fan);
        activityFan.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        busyVisual.addView(activityFan,new LinearLayout.LayoutParams(dp(36),dp(36)));
        android.widget.ProgressBar activityBar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        activityBar.setIndeterminate(true);activityBar.setIndeterminateTintList(ColorStateList.valueOf(BLUE));
        LinearLayout.LayoutParams activityLp=new LinearLayout.LayoutParams(0,dp(8),1);activityLp.setMargins(dp(16),0,0,0);busyVisual.addView(activityBar,activityLp);
        add(detect,busyVisual,12);busyVisual.setVisibility(View.GONE);
        codeStages=column();add(detect,codeStages,10);codeStages.setVisibility(View.GONE);
        progress=text("",14,GREY,false);progress.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(detect,progress,8);
        stopButton=secondaryButton("Przerwij wyszukiwanie");add(detect,stopButton,9);
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v->{cancel.set(true);setInfo("Zatrzymywanie wyszukiwania...");});
        extendedScan=new CheckBox(this);
        extendedScan.setText("Pełne skanowanie wszystkich adresów (dłuższe)");
        extendedScan.setTextColor(GREY);
        // Advanced-only selection below.

        editorPanel=card();add(root,editorPanel,0);
        add(editorPanel,text("2. Zmień adres silnika",18,WHITE,true),0);
        purposeSpinner=spinner("NAWIEW — 231, 233, 235…", "WYWIEW — 232, 234, 236…", "Własne ustawienia");
        add(editorPanel,purposeSpinner,13);
        label(editorPanel,"NUMER SILNIKA W NAWIEWIE / WYWIEWIE");
        motorNumberSpinner=spinner("1", "2", "3", "4", "5", "6", "7", "8");
        add(editorPanel,motorNumberSpinner,5);
        add(editorPanel,text("Wybierz funkcję i numer silnika. Ustawienia: 9600 / 8N2, CM=3. Adres możesz też wpisać ręcznie.",13,GREY,false),9);
        label(editorPanel,"NOWY ADRES MODBUS (1–247)");
        addressEdit=number(231);add(editorPanel,addressEdit,6);
        addressEdit.setContentDescription("Nowy adres silnika od 1 do 247");
        writeButton=button("ZMIEŃ PARAMETRY");add(editorPanel,writeButton,18);
        writeButton.setOnClickListener(v->confirmWrite());
        writeHint=text("Najpierw wykryj silnik.",12,GREY,false);add(editorPanel,writeHint,9);

        manualConfigPanel=column();add(editorPanel,manualConfigPanel,5);
        label(manualConfigPanel,"PRĘDKOŚĆ");
        baudSpinner=spinner("9600","19200","4800","38400","115200");add(manualConfigPanel,baudSpinner,5);
        label(manualConfigPanel,"TRYB KOMUNIKACJI");
        modeSpinner=spinner("8N2 (None / 2)","8E1 (Even / 1)","8N1 (None / 1)","8O1 (Odd / 1)");
        add(manualConfigPanel,modeSpinner,5);
        manualConfigPanel.setVisibility(View.GONE);
        purposeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int index,long id){
                manualConfigPanel.setVisibility(index==2 ? View.VISIBLE : View.GONE);
                updateSuggestedAddress();
                if(index<2){baudSpinner.setSelection(0);modeSpinner.setSelection(0);}
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });

        motorNumberSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int index,long id){updateSuggestedAddress();}
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });

        factoryButton=secondaryButton("USTAWIENIA FABRYCZNE — 247 / 19200 / Even / 1");
        add(root,factoryButton,9);
        factoryButton.setOnClickListener(v->showFactoryOptions());

        LinearLayout expert=card(); add(root,expert,0);
        manualToggleButton=secondaryButton("▸  Więcej ustawień"); add(expert,manualToggleButton,0);
        manualFields=column();add(expert,manualFields,8);
        manualFields.setVisibility(View.GONE);
        manualToggleButton.setOnClickListener(v->{
            boolean show=manualFields.getVisibility()!=View.VISIBLE;
            manualFields.setVisibility(show ? View.VISIBLE : View.GONE);
            manualToggleButton.setText(show ? "▾  Ukryj ustawienia" : "▸  Więcej ustawień");
        });
        add(manualFields,extendedScan,6);
        label(manualFields,"SZUKAJ ADRESÓW OD");
        manualFromAddress=number(1);add(manualFields,manualFromAddress,4);
        label(manualFields,"DO");
        manualToAddress=number(231);add(manualFields,manualToAddress,4);
        label(manualFields,"PRĘDKOŚĆ WYSZUKIWANIA");
        manualBaud=spinner("19200","9600","4800","38400","57600","115200","14400");add(manualFields,manualBaud,4);
        label(manualFields,"TRYB WYSZUKIWANIA");
        manualMode=spinner("8E1 (Even / 1)","8N2 (None / 2)","8N1 (None / 1)","8O1 (Odd / 1)","8E2 (Even / 2)","8O2 (Odd / 2)");add(manualFields,manualMode,4);
        label(manualFields,"CZAS ODPOWIEDZI (ms)");
        manualTimeout=number(700);add(manualFields,manualTimeout,4);
        manualButton=secondaryButton("Szukaj w podanym zakresie");add(manualFields,manualButton,9);
        manualButton.setOnClickListener(v->requestManualScan());
        readResult=text("Po wykryciu zobaczysz tutaj model, aktywny adres i zapisane ustawienia.",16,WHITE,false);
        readResult.setTextIsSelectable(true);
        add(detect,readResult,12);
        reportButton=secondaryButton("Kopiuj raport techniczny");add(detect,reportButton,8);
        reportButton.setOnClickListener(v->{
            ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Raport Clima Gold",lastScanReport));
            Toast.makeText(this,"Skopiowano raport",Toast.LENGTH_SHORT).show();
        });
        info=text("Podłącz jeden silnik do kabla RS485.",12,GREY,false);
        add(root,info,3);
        refreshWriteButton();
    }

    private void updateUsbStatus() {
        List<UsbSerialDriver> found = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        chosenDriver = found.isEmpty() ? null : found.get(0);
        if (chosenDriver == null) {
            usbStatus.setText("Nie znaleziono adaptera USB–RS485. Podłącz kabel przez USB OTG.");
        } else {
            UsbDevice d = chosenDriver.getDevice();
            usbStatus.setText("Wykryto USB: VID " + d.getVendorId() + " / PID " + d.getProductId() +
                    (usbManager.hasPermission(d) ? " • dostęp przyznany" : " • wymagana zgoda Androida"));
        }
    }

    private void requestScan() { requestUsbPermission(false); }
    private void requestManualScan() {
        if (busy) return;
        try { getManualLink(); }
        catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Niepoprawne ustawienia wyszukiwania")
                .setMessage(e.getMessage()).setPositiveButton("OK",null).show();
            return;
        }
        requestUsbPermission(true);
    }
    private void requestUsbPermission(boolean manual) {
        if (busy || pendingStart) return;
        updateUsbStatus();
        if (chosenDriver == null) { setInfo("Podłącz adapter USB–RS485 przez USB OTG."); return; }
        UsbDevice device = chosenDriver.getDevice();
        if (!usbManager.hasPermission(device)) {
            pendingStart=true;
            detectButton.setEnabled(false);
            pendingManual=manual;
            Intent intent = new Intent(USB_ACTION).setPackage(getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
            PendingIntent permissionIntent=PendingIntent.getBroadcast(this, 0, intent, flags);
            usbManager.requestPermission(device, permissionIntent);
        } else if (manual) startManualScan();
        else startScan();
    }
    private static final class ManualSettings {
        final SerialBridge.Link link;
        final AddressRange range;
        final int timeout;
        ManualSettings(SerialBridge.Link l, AddressRange r, int t) {
            link=l; range=r; timeout=t;
        }
    }
    private ManualSettings getManualLink() {
        final int from, to, baud, timeout;
        try {
            from = Integer.parseInt(manualFromAddress.getText().toString().trim());
            to = Integer.parseInt(manualToAddress.getText().toString().trim());
            baud = Integer.parseInt(manualBaud.getSelectedItem().toString());
            timeout = Integer.parseInt(manualTimeout.getText().toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Wpisz liczby w polach «Adres od», «Adres do» i czasie odpowiedzi.");
        }
        AddressRange range=new AddressRange(from,to);
        if(timeout<150 || timeout>3000)
            throw new IllegalArgumentException("Czas odpowiedzi musi wynosić 150–3000 ms.");
        String mode=manualMode.getSelectedItem().toString().substring(0,3);
        int parity=mode.charAt(1)=='E' ? UsbSerialPort.PARITY_EVEN :
                   mode.charAt(1)=='O' ? UsbSerialPort.PARITY_ODD : UsbSerialPort.PARITY_NONE;
        int stop=mode.charAt(2)=='2' ? UsbSerialPort.STOPBITS_2 : UsbSerialPort.STOPBITS_1;
        SerialBridge.Link link=new SerialBridge.Link(baud,parity,stop,baud+" / "+
                (mode.charAt(1)=='E'?"Even":mode.charAt(1)=='O'?"Odd":"None")+" / "+mode.charAt(2));
        return new ManualSettings(link,range,timeout);
    }
    /** Read-only scan over an inclusive range. Stops on the first valid MODBUS response. */
    private void startManualScan() {
        if (busy || chosenDriver == null) return;
        final ManualSettings settings;
        try { settings=getManualLink(); }
        catch(Exception e) { setInfo("Niepoprawne ustawienia: "+e.getMessage()); return; }
        cancel.set(false);
        foundLink=null; foundAddress=-1; rawCom=-1; foundProductCode=-1; foundFirmware=-1; compatibleWriteProfile=false;
        editorPanel.setVisibility(View.GONE);
        readResult.setText("");
        deviceStatus.setText("Ręczne szukanie urządzenia…");
        setProgress("Zakres " + settings.range.first + "–" + settings.range.last + " • " + settings.link.label);
        lastScanReport="Trwa wyszukiwanie ręczne…";
        setBusy(true);
        final UsbSerialDriver driver=chosenDriver;
        jobs.execute(()->{
            Hit hit=null;
            String error="";
            int[] requests={0};
            int scanned=0;
            try(SerialBridge io=new SerialBridge(usbManager,driver)) {
                io.open();
                io.configure(settings.link);
                for (int addr=settings.range.first; addr<=settings.range.last && !cancel.get(); addr++) {
                    if ((addr-settings.range.first)%5==0 || addr==settings.range.last) {
                        setProgress("Sprawdzam adres "+addr+" z "+settings.range.last+
                                    " • "+settings.link.label);
                    }
                    scanned++;
                    hit=detectAt(io,settings.link,addr,settings.timeout,requests);
                    if(hit!=null) break;
                }
                if(hit!=null && !cancel.get()) hit=inspectDevice(io,hit,settings.timeout);
            } catch(Exception e) { error=e.getClass().getSimpleName()+": "+e.getMessage(); }
            final Hit result=hit;
            final String err=error;
            final int scannedFinal=scanned;
            final boolean interrupted=cancel.get();
            final String report="Clima Gold ZIEHL-ABEGG CM3 TEST 1.0.3 • SZUKANIE RĘCZNE\n"+
                    "Zakres: "+settings.range.first+"–"+settings.range.last+
                    "\nKomunikacja: "+settings.link.label+
                    "\nTimeout: "+settings.timeout+" ms\nSprawdzone adresy: "+scannedFinal+
                    "\nZapytania: "+requests[0]+"\n"+
                    (hit==null?(interrupted?"Przerwano wyszukiwanie\n":"Brak odpowiedzi\n"):
                     "Wykryto adres: "+hit.address+" • "+hit.method()+" • wyjątek "+hit.exception+"\n"+
                    "Kod produktu: "+AutoProfile.productLabel(hit.productCode)+"\n")+
                    (error.isEmpty()?"":"Błąd: "+error+"\n");
            handler.post(()->{
                lastScanReport=report;
                if(result!=null) {
                    showDetection(result);
                    notifyDetected(result);
                    setInfo("Wykryto urządzenie MODBUS na adresie "+result.address+". Sprawdź jego zgodność zapisu.");
                } else if(interrupted) {
                    deviceStatus.setText("Wyszukiwanie przerwane.");
                    setInfo("Przerwano szukanie w zakresie adresów. Możesz skopiować raport.");
                } else {
                    deviceStatus.setText("Nie znaleziono odpowiedzi w zakresie "+
                                         settings.range.first+"–"+settings.range.last);
                    readResult.setText("");
                    setInfo("Sprawdź parametry komunikacji, przewody i raport. "+err);
                }
                setBusy(false);
            });
        });
    }
    /** Factory communication defaults are not the same as a full factory reset. */
    private void showFactoryOptions() {
        if(busy) return;
        new AlertDialog.Builder(this)
            .setTitle("Ustawienia fabryczne silnika")
            .setMessage("Możesz przygotować fabryczne ustawienia komunikacji MODBUS: 247 / 19200 / Even / 1.\n\n"+
                        "To NIE jest pełny reset fabryczny silnika. Nie przywraca np. ustawień pracy, sterowania czy limitów obrotów. "+
                        "Fabryczne wartości mogą zależeć od modelu. Zapis wymaga weryfikacji dokumentacji.")
            .setPositiveButton("Ustaw MODBUS 247", (d,w)->{
                purposeSpinner.setSelection(2); // pokaż pola ręczne
                addressEdit.setText("247");
                baudSpinner.setSelection(1); // 19200
                modeSpinner.setSelection(1); // 8E1
                setInfo("Ustawiono fabryczny profil komunikacji 247 / 19200 / 8E1. Jeszcze nic nie zapisano.");
                refreshWriteButton();
            })
            .setNeutralButton("Pełny reset – informacje", (d,w)->{
                new AlertDialog.Builder(this)
                    .setTitle("Pełny reset fabryczny")
                    .setMessage("W niektórych urządzeniach producent przewiduje przywrócenie fabryczne, ale sposób i zakres resetu zależą od modelu i uprawnień. "+
                                "Ta wersja NIE wykonuje pełnego resetu, aby przypadkowo nie zmienić parametrów silnika. "+
                                "Potrzebna jest dokładna instrukcja wykrytego modelu.")
                    .setPositiveButton("Rozumiem",null).show();
            })
            .setNegativeButton("Anuluj",null).show();
    }
    private void setInfo(String value) { handler.post(()->info.setText(value)); }
    private void setProgress(String value) { handler.post(()->{
        if (simpleWizard()) {
            boolean programming = value.length() > 3 && value.charAt(1) == '/' && value.charAt(2) == '5';
            int phase = programming ? (value.charAt(0) >= '5' ? 2 : 1)
                : value.startsWith("✓") || value.startsWith("Nie potwierdzono") ? 2 : 0;
            progress.setText(getIntent().hasExtra("verify_addresses") ? "Sprawdzam: " + value.replace("Sprawdzanie: ", "").replaceAll(" — adres [0-9]+", "")
                : new String[]{"Szukam wentylatora. To może potrwać kilka minut.", "Zapisuję ustawienia. Poczekaj.", "Sprawdzam zapisane ustawienia."}[phase]);
            codeStages.removeAllViews();
            codeStages.addView(getIntent().hasExtra("verify_addresses")
                ? Visuals.steps(this, new String[]{"Końcowe sprawdzenie"}, 0)
                : Visuals.steps(this, new String[]{"Wyszukiwanie", "Kodowanie", "Sprawdzenie"}, phase));
            codeStages.setVisibility(View.VISIBLE);
            return;
        }
        progress.setText(value);
        progress.setTextColor(value.startsWith("✓")?Visuals.GREEN:GREY);
        if(value.length()>3 && value.charAt(1)=='/' && value.charAt(2)=='5'){
            int stage=value.charAt(0)-'1';
            if(stage>=0 && stage<5){codeStages.removeAllViews();codeStages.addView(Visuals.steps(this,new String[]{"Odczyt","Adres","Zastosuj","CM=3","Kontrola"},stage));codeStages.setVisibility(View.VISIBLE);}
        }
    }); }
    private void animateActivity(boolean active){
        if(fanSpin!=null){fanSpin.cancel();fanSpin=null;}
        activityFan.setRotation(0);
        if(active && android.animation.ValueAnimator.areAnimatorsEnabled()){
            fanSpin=android.animation.ObjectAnimator.ofFloat(activityFan,"rotation",0,360);
            fanSpin.setDuration(1600);fanSpin.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            fanSpin.setInterpolator(new android.view.animation.LinearInterpolator());fanSpin.start();
        }
    }
    @Override protected void onStart(){super.onStart();if(activityFan!=null && busy)animateActivity(true);}
    @Override protected void onStop(){if(activityFan!=null)animateActivity(false);super.onStop();}
    private void setBusy(boolean b) {
        busy=b;
        handler.post(()->{
            busyVisual.setVisibility(b?View.VISIBLE:View.GONE);animateActivity(b);
            if(b){codeStages.setVisibility(View.GONE);progress.setTextColor(GREY);}
            menuBackButton.setEnabled(!b);
            detectButton.setEnabled(!b);
            if(manualButton!=null) manualButton.setEnabled(!b);
            if(factoryButton!=null) factoryButton.setEnabled(!b);
            stopButton.setEnabled(b);
            addressEdit.setEnabled(!b);
            purposeSpinner.setEnabled(!b);
            motorNumberSpinner.setEnabled(!b);
            baudSpinner.setEnabled(!b);
            modeSpinner.setEnabled(!b);
            if(!b)applyGuidedTarget();
            refreshWriteButton();
        });
    }
    private boolean knownWriteProfile(){
        return foundLink!=null && foundAddress>=1 &&
          (foundProductCode==0x0100 || foundProductCode==0x0101) &&
          ModbusRtu.resemblesEcblueComParameter(rawCom);
    }
    private String writeBlockReason(){
        if(busy) return "Trwa inna operacja.";
        if(foundLink==null || foundAddress<1) return "Najpierw wykryj silnik.";
        if(foundProductCode!=0x0100 && foundProductCode!=0x0101)
            return "Adres został odczytany, ale zapis dla tego modelu nie jest obsługiwany.\n"+
                    AutoProfile.productLabel(foundProductCode)+
                    "\nSkopiuj raport techniczny, aby dobrać właściwą procedurę.";
        if(!ModbusRtu.resemblesEcblueComParameter(rawCom))
            return "Nie potwierdzono rejestru ustawień komunikacji h3. Sam odczyt adresu nie wystarcza do zapisu. Skopiuj raport techniczny.";
        return null;
    }
    private void refreshWriteButton(){
        if(writeButton==null) return;
        writeButton.setEnabled(!busy);
        writeButton.setText("ZMIEŃ PARAMETRY");
        if(writeHint!=null){
            String reason=writeBlockReason();
            writeHint.setText(reason==null ? "Gotowe do zmiany parametrów." : reason);
            writeHint.setTextColor(reason==null?GREEN:GREY);
        }
    }

    private static final class Hit {
        final SerialBridge.Link link;
        final int address, raw, function, register, exception;
        final int productCode, firmware, controlWord;
        Hit(SerialBridge.Link link, int address, int raw, int fn, int reg, int ex) {
            this(link,address,raw,fn,reg,ex,-1,-1);
        }
        Hit(SerialBridge.Link link, int address, int raw, int fn, int reg, int ex,
            int productCode, int firmware) {
            this(link,address,raw,fn,reg,ex,productCode,firmware,-1);
        }
        Hit(SerialBridge.Link link, int address,int raw,int fn,int reg,int ex,int productCode,int firmware,int controlWord) {
            this.controlWord=controlWord;
            this.link=link; this.address=address; this.raw=raw;
            this.function=fn; this.register=reg; this.exception=ex;
            this.productCode=productCode; this.firmware=firmware;
        }
        String method() { return "FC"+String.format(java.util.Locale.US,"%02d",function)+" / rejestr " +register; }
    }

    /** Manufacturer identification is read separately from a generic MODBUS response. */
    private Hit inspectDevice(SerialBridge io, Hit hit, int timeout) {
        int h3=-1,code=-1,fw=-1,control=-1;
        try {
            ModbusRtu.Reply a=io.transaction(hit.address,3,3,0,Math.max(350,timeout));
            if(a!=null && a.successful()) h3=a.value;
        } catch(Exception ignored) {}
        try {
            ModbusRtu.Reply a=io.transaction(hit.address,4,1,0,Math.max(350,timeout));
            if(a!=null && a.successful()) code=a.value;
        } catch(Exception ignored) {}
        try {
            ModbusRtu.Reply a=io.transaction(hit.address,4,0,0,Math.max(350,timeout));
            if(a!=null && a.successful()) fw=a.value;
        } catch(Exception ignored) {}
        if(code==0x0100 || code==0x0101) {
            try {
                ModbusRtu.Reply value=io.transaction(hit.address,3,4,0,Math.max(350,timeout));
                if(value!=null && value.successful())control=value.value;
            } catch(Exception ignored) { }
        }
        return new Hit(hit.link,hit.address,h3,hit.function,hit.register,hit.exception,code,fw,control);
    }

    /** A valid response does not yet establish a model or its register layout. */
    private Hit probe(SerialBridge io, SerialBridge.Link link, int address,
                      int function, int register, int timeout) throws IOException {
        ModbusRtu.Reply reply=io.transaction(address,function,register,0,timeout);
        if (reply == null) return null;
        int raw=(function==3 && register==3 && reply.successful()) ? reply.value : -1;
        return new Hit(link,address,raw,function,register,reply.exception);
    }

    /** Read-only requests; an exception with valid CRC also proves device is present. */
    private Hit detectAt(SerialBridge io, SerialBridge.Link link, int address,
                         int timeout, int[] counters) throws IOException {
        final int[][] reads={{3,3},{3,0},{4,0},{4,1}};
        for(int[] r:reads) {
            if(cancel.get()) return null;
            counters[0]++;
            Hit h=probe(io,link,address,r[0],r[1],timeout);
            if(h!=null) return h;
        }
        return null;
    }

    private void showDetection(Hit result) {
        foundAddress=result.address; foundLink=result.link; rawCom=result.raw;
        foundProductCode=result.productCode; foundFirmware=result.firmware;
        compatibleWriteProfile=AutoProfile.canWrite(result.productCode,result.raw,
                result.address,result.link.baud,result.link.parity,result.link.stop);
        deviceStatus.setText("Wykryto urządzenie • adres " +result.address);
        progress.setText("Połączenie: " +result.link.label+" • "+result.method());
        String identity=AutoProfile.productLabel(result.productCode);
        String firmware=result.firmware>=0?" • firmware "+result.firmware:"";
        String com=result.raw>=0 && ModbusRtu.resemblesEcblueComParameter(result.raw)
                ?ModbusRtu.description(result.raw):"Niepotwierdzony rejestr komunikacji";
        boolean pending=AutoProfile.pendingActivation(result.productCode,result.raw,
                result.address,result.link.baud,result.link.parity,result.link.stop);
        readResult.setText("Identyfikacja: " +identity + firmware +
            "\nAKTYWNE połączenie: "+result.address+" / "+result.link.label+
            "\nZAPISANE w h3: "+com+
            "\nCM (odczyt / cel): "+(result.controlWord>=0?String.valueOf(result.controlWord&15):"brak odczytu")+" / 3"+
            (compatibleWriteProfile ? "\nZgodność rejestru h3 i aktywnej komunikacji potwierdzona."
            : pending ? "\nZapisane parametry różnią się od aktywnych. Wpisz zapisany adres jako cel, aby zastosować go bez ponownego zapisu h3."
                      : "\nBrak zweryfikowanego profilu zapisu — dostępny tylko odczyt."));
        purposeSpinner.setSelection(2);
        addressEdit.setText(String.valueOf(result.address));
        String[] baudValues={"9600","19200","4800","38400","115200"};
        for(int i=0;i<baudValues.length;i++)
            if(baudValues[i].equals(String.valueOf(result.link.baud))) baudSpinner.setSelection(i);
        int modeIndex=result.link.parity==UsbSerialPort.PARITY_EVEN?1:
                result.link.parity==UsbSerialPort.PARITY_ODD?3:result.link.stop==2?0:2;
        modeSpinner.setSelection(modeIndex);
        editorPanel.setVisibility(View.VISIBLE);
        if (simpleWizard()) {
            editorPanel.setVisibility(View.GONE);
            deviceStatus.setText("Wentylator odnaleziony");
            progress.setText("Przygotowuję ustawienia.");
        }
        applyGuidedTarget();
        refreshWriteButton();
    }

    private void notifyDetected(Hit hit){
        String summary=knownWriteProfile() ? "Silnik gotowy do zmiany parametrów." :
                       "Silnik wykryty, ale zapis dla tego modelu nie jest dostępny.";
        Toast.makeText(this,"Wykryto silnik: adres "+hit.address,Toast.LENGTH_LONG).show();
        new AlertDialog.Builder(this).setTitle("Wykryto silnik")
          .setMessage("Adres: "+hit.address+"\n"+summary)
          .setPositiveButton("OK",null).show();
    }

    private void startScan() {
        if(busy || chosenDriver==null) return;
        if(getIntent().hasExtra("verify_addresses")){verifyGuidedMotors();return;}
        cancel.set(false);
        final boolean extended=extendedScan.isChecked();
        foundLink=null; foundAddress=-1; rawCom=-1; foundProductCode=-1; foundFirmware=-1; compatibleWriteProfile=false;
        editorPanel.setVisibility(View.GONE);
        deviceStatus.setText("Wyszukiwanie urządzeń MODBUS…");
        readResult.setText("");
        if (simpleWizard() && !lastScanReport.equals("Nie uruchomiono jeszcze skanowania."))
            guidedAttemptHistory += "\nPOPRZEDNIA PRÓBA:\n" + lastScanReport + "\n";
        lastScanReport="Trwa skanowanie…";
        setBusy(true);
        final UsbSerialDriver driver=chosenDriver;
        jobs.execute(()->{
            Hit hit=null;
            String error="";
            int[] counters={0};
            int profiles=0;
            StringBuilder report=new StringBuilder();
            report.append("Clima Gold ZIEHL-ABEGG CM3 Android TEST 1.3.1\n")
                  .append("Adapter VID=").append(driver.getDevice().getVendorId())
                  .append(" PID=").append(driver.getDevice().getProductId()).append("\n")
                  .append("Tryb rozszerzony: ").append(extended).append("\n")
                  .append("Testowane protokoły: MODBUS RTU FC03 h3, FC03 h0, FC04 i0, FC04 i1.\n");
            try(SerialBridge io=new SerialBridge(usbManager,driver)) {
                io.open();
                // Stage 1: scan company addresses in descending order.
                for(int quickIndex=0;quickIndex<4;quickIndex++) {
                    SerialBridge.Link link=SerialBridge.COMMON_LINKS[quickIndex];
                    if(cancel.get() || hit!=null) break;
                    try { io.configure(link); profiles++; }
                    catch(Exception e) { report.append("Pominięto ").append(link.label).append(": ").append(e.getMessage()).append("\n"); continue; }
                    setProgress("Szybkie: " +link.label+" • 247 → 200");
                    for(int addr=247;addr>=200;addr--) {
                        if(cancel.get()) break;
                        setProgress("Szybkie: "+link.label+" • adres "+addr+" (247 → 200)");
                        hit=detectAt(io,link,addr,240,counters);
                        if(hit!=null) break;
                    }
                }
                // Stage 2: scan every address on the most likely links.
                for(int idx=0;extended && idx<4 && hit==null && !cancel.get();idx++) {
                    SerialBridge.Link link=SerialBridge.COMMON_LINKS[idx];
                    try { io.configure(link); profiles++; }
                    catch(Exception e) { report.append("Pominięto ").append(link.label).append(": ").append(e.getMessage()).append("\n"); continue; }
                    for(int addr=1;addr<=247 && hit==null && !cancel.get();addr++) {
                        if(addr%8==1) setProgress("Pełne: " +link.label+" • adres "+addr+"/247");
                        hit=detectAt(io,link,addr,220,counters);
                    }
                }
                // Stage 3: extra modes after the user explicitly requests a longer scan.
                if(extended && hit==null && !cancel.get()) {
                    for(int idx=4;idx<SerialBridge.COMMON_LINKS.length && hit==null && !cancel.get();idx++) {
                        SerialBridge.Link link=SerialBridge.COMMON_LINKS[idx];
                        try { io.configure(link); profiles++; }
                        catch(Exception e) { report.append("Pominięto ").append(link.label).append(": ").append(e.getMessage()).append("\n"); continue; }
                        for(int addr=1;addr<=247 && hit==null && !cancel.get();addr++) {
                            if(addr%8==1) setProgress("Rozszerzone: " +link.label+" • adres "+addr+"/247");
                            hit=detectAt(io,link,addr,250,counters);
                        }
                    }
                }
                if(hit!=null && !cancel.get()) {
                    io.configure(hit.link);
                    hit=inspectDevice(io,hit,450);
                }
            } catch(Exception e) { error=e.getClass().getSimpleName()+": "+e.getMessage(); }
            report.append("Sprawdzone profile: ").append(profiles).append("\n")
                  .append("Liczba zapytań: ").append(counters[0]).append("\n");
            if(!error.isEmpty()) report.append("Błąd: ").append(error).append("\n");
            if(hit!=null) {
                report.append("WYKRYTO na adresie ").append(hit.address)
                      .append(" • ").append(hit.link.label)
                      .append(" • ").append(hit.method()).append("\n")
                      .append("MODBUS exception: ").append(hit.exception).append("\n")
                      .append("AKTYWNY adres i tryb: ").append(hit.address).append(" / ").append(hit.link.label).append("\n")
                      .append("ZAPISANY adres i tryb z h3: ")
                      .append(hit.raw>=0 && ModbusRtu.resemblesEcblueComParameter(hit.raw) ? ModbusRtu.description(hit.raw) : "NIEPOTWIERDZONE").append("\n")
                      .append("h3 odczyt: ").append(hit.raw>=0 ? String.format(java.util.Locale.US,"0x%04X",hit.raw) : "BRAK").append("\n")
                      .append("Kod produktu i1: ").append(AutoProfile.productLabel(hit.productCode)).append("\n")
                      .append("Firmware i0: ").append(hit.firmware).append("\n")
                      .append("CM odczyt/cel: ").append(hit.controlWord>=0?String.valueOf(hit.controlWord&15):"BRAK").append("/3\n");
            } else report.append(cancel.get()?"PRZERWANO\n":"BRAK ODPOWIEDZI\n");
            final Hit result=hit;
            final String finalError=error;
            final String finalReport=report.toString();
            handler.post(()->{
                lastScanReport=finalReport;
                if (simpleWizard()) {
                    setBusy(false);
                    if (result != null && !cancel.get() && finalError.isEmpty()) {
                        showDetection(result);
                        String blocked = writeBlockReason();
                        if (blocked != null) {
                            guidedFailure("Nie rozpoznano obsługiwanego wentylatora. Poproś instalatora o sprawdzenie modelu.");
                        } else {
                            int target = guidedTarget();
                            writeConfig(target, 9600, "8N2", ModbusRtu.configValue(target, 9600, "8N2"));
                        }
                    } else guidedFailure(cancel.get() ? "Wyszukiwanie przerwane. Niczego nie zakodowano."
                        : "Brak odpowiedzi. Sprawdź zasilanie wentylatora, adapter USB i przewody RS485.");
                    return;
                }
                if(result!=null) {
                    showDetection(result);
                    notifyDetected(result);
                    setInfo(knownWriteProfile() ?
                            "Silnik wykryty. Wybierz ustawienie i naciśnij «ZMIEŃ PARAMETRY»." :
                            "Silnik wykryty. Zapis dla tego modelu jest niedostępny.");
                } else if(cancel.get()) {
                    deviceStatus.setText("Wyszukiwanie przerwane.");
                    setInfo("Przerwano na żądanie. Możesz skopiować raport.");
                } else {
                    deviceStatus.setText("Nie wykryto silnika.");
                    progress.setText(extended?"Pełny skan: brak odpowiedzi MODBUS.":"Szybki skan zakończony: brak odpowiedzi MODBUS.");
                    setInfo("Sprawdź przewody RS485 i zgodność adaptera. " +
                            (extended?"":"Pełne skanowanie uruchomisz w «Więcej ustawień». ")+
                            "Skopiuj raport i wyślij do analizy. "+finalError);
                }
                setBusy(false);
            });
        });
    }

    private void updateSuggestedAddress(){
        int purpose=purposeSpinner.getSelectedItemPosition();
        if(purpose<0 || purpose>1 || motorNumberSpinner==null) return;
        int number=motorNumberSpinner.getSelectedItemPosition()+1;
        addressEdit.setText(String.valueOf(AddressInput.ventilationAddress(purpose==0,number)));
    }

    private void confirmWrite(){
        String reason=writeBlockReason();
        if(reason!=null){new AlertDialog.Builder(this).setTitle("Nie można zmienić parametrów")
                .setMessage(reason).setPositiveButton("OK",null).show();return;}
        final int address,baud,config;
        final String mode;
        try {
            int preset=purposeSpinner.getSelectedItemPosition();
            address=guidedTarget()>0?guidedTarget():AddressInput.parse(addressEdit.getText().toString());
            baud=(guidedTarget()>0 || preset<2)?9600:Integer.parseInt(baudSpinner.getSelectedItem().toString());
            mode=(guidedTarget()>0 || preset<2)?"8N2":modeSpinner.getSelectedItem().toString().substring(0,3);
            config=ModbusRtu.configValue(address,baud,mode);
            if(baud==38400 || baud==115200)
                throw new IllegalArgumentException("Ta prędkość nie ma potwierdzonej obsługi zapisu.");
        } catch(Exception e){setInfo("Błędne ustawienia: "+e.getMessage());return;}
        new AlertDialog.Builder(this)
          .setTitle("Zmienić ustawienia silnika?")
          .setMessage("Aktualny adres: "+foundAddress+"\nNowy adres: "+address+"\nKomunikacja: "+baud+" / "+mode+
              "\nTryb sterowania CM: 3\n\nPotwierdź, że podłączony jest tylko jeden zatrzymany silnik, odłączony od pracującej centrali. "+
              "Aplikacja zapisze ustawienia i spróbuje je zastosować. Po operacji sprawdzi wynik.")
          .setNegativeButton("Anuluj",null)
          .setPositiveButton("Zmień parametry",(d,w)->writeConfig(address,baud,mode,config)).show();
    }

    private static SerialBridge.Link linkFor(int baud, String mode) {
        int parity=mode.charAt(1)=='E' ? UsbSerialPort.PARITY_EVEN :
                   mode.charAt(1)=='O' ? UsbSerialPort.PARITY_ODD : UsbSerialPort.PARITY_NONE;
        int stop=mode.charAt(2)=='2' ? UsbSerialPort.STOPBITS_2 : UsbSerialPort.STOPBITS_1;
        return new SerialBridge.Link(baud,parity,stop,baud+" / "+
                (parity==UsbSerialPort.PARITY_EVEN?"Even":
                 parity==UsbSerialPort.PARITY_ODD?"Odd":"None")+" / "+stop);
    }

    /** ONE action: h3 write (if needed), h0=3698 activation, read-only verification.
     * Never re-send a write automatically when ACK disappears. */
    private void writeConfig(int address,int baud,String mode,int newValue) {
        if(busy)return;
        setBusy(true);
        handler.post(()->stopButton.setEnabled(false));
        setInfo("Trwa zmiana parametrów i sprawdzanie nowego połączenia. Poczekaj na wynik.");
        contentScroll.smoothScrollTo(0,0);
        final int oldAddr=foundAddress,oldWord=rawCom,productCode=foundProductCode,firmware=foundFirmware;
        final SerialBridge.Link oldLink=foundLink;
        final UsbSerialDriver driver=chosenDriver;
        jobs.execute(()->{
            CommunicationProgrammer.Result result;
            String rawTrace="";
            try(SerialBridge io=new SerialBridge(usbManager,driver)){
                io.open();
                CommunicationProgrammer programmer=new CommunicationProgrammer(new CommunicationProgrammer.Transport() {
                    public void configure(int speed,String format) throws IOException { io.configure(linkFor(speed,format)); }
                    public ModbusRtu.Reply transaction(int slave,int fn,int reg,int value,int timeout) throws IOException {
                        return io.transaction(slave,fn,reg,value,timeout);
                    }
                },new RtuSession.Clock() {
                    public long now() { return android.os.SystemClock.elapsedRealtime(); }
                    public void sleep(long millis) { android.os.SystemClock.sleep(millis); }
                });
                programmer.setProgressListener(this::setProgress);
                String oldMode="8"+(oldLink.parity==UsbSerialPort.PARITY_EVEN?"E":
                        oldLink.parity==UsbSerialPort.PARITY_ODD?"O":"N")+oldLink.stop;
                result=programmer.run(new CommunicationProgrammer.Settings(oldAddr,oldLink.baud,oldMode),
                        productCode,oldWord,newValue);
                rawTrace=io.diagnostics();
            }catch(Exception e){
                result=new CommunicationProgrammer.Result(false,false,true,
                        "Operacja przerwana. Wykryj silnik ponownie.",e.toString());
            }
            final String finalTitle=result.success?"Ustawienia potwierdzone":result.pending?
                    "Zapisano, aktywacja niepotwierdzona":"Nie potwierdzono zmiany";
            final String finalText=result.message,finalLog=result.details+"\nRAMKI RTU:\n"+rawTrace;
            final boolean didSend=result.attempted,confirmed=result.success;
            final int confirmedH4=result.controlWord;
            handler.post(()->{
                setBusy(false);
                lastScanReport += "\n\nZAPIS (PROSTA):\n"+finalLog;
                if (simpleWizard()) {
                    if (confirmed) {
                        Intent data = new Intent();
                        data.putExtra("confirmed_address", address);
                        data.putExtra("report", GuidedSession.motorLabel(address) + "\n" + guidedAttemptHistory + lastScanReport);
                        setResult(RESULT_OK, data); finish();
                    } else {
                        foundAddress = -1; foundLink = null; rawCom = -1; compatibleWriteProfile = false;
                        guidedFailure("Nie potwierdzono ustawień. Nie przechodź do następnego wentylatora.");
                    }
                    return;
                }
                info.setText(finalText);
                new AlertDialog.Builder(this).setTitle(finalTitle)
                  .setMessage(finalText).setPositiveButton(guidedTarget()>0 && confirmed?"Wróć do kreatora":"OK",(dialog,which)->{
                      if(guidedTarget()>0 && confirmed){Intent data=new Intent();data.putExtra("confirmed_address",address);data.putExtra("report",GuidedSession.motorLabel(address)+"\n"+lastScanReport);setResult(RESULT_OK,data);finish();}
                  }).show();
                if(confirmed){
                    showDetection(new Hit(linkFor(baud,mode),address,newValue,3,3,0,productCode,firmware,confirmedH4));
                    deviceStatus.setText("Potwierdzono adres " + address);
                } else if(didSend){
                    readResult.setText(finalText);
                    foundAddress=-1;foundLink=null;rawCom=-1;foundProductCode=-1;
                    foundFirmware=-1;compatibleWriteProfile=false;
                    deviceStatus.setText("Wykryj silnik ponownie, żeby potwierdzić ustawienia.");
                    refreshWriteButton();
                }
            });
        });
    }

    private void verifyGuidedMotors(){
        final int[] addresses=getIntent().getIntArrayExtra("verify_addresses");
        if(addresses==null || addresses.length==0)return;
        cancel.set(false);setBusy(true);editorPanel.setVisibility(View.GONE);
        final UsbSerialDriver driver=chosenDriver;
        jobs.execute(()->{
            StringBuilder report=new StringBuilder("KOŃCOWY TEST — 9600 / 8N2, CM=3\n");
            boolean ok=true;
            try(SerialBridge io=new SerialBridge(usbManager,driver)){
                io.open();io.configure(linkFor(9600,"8N2"));
                for(int address:addresses){
                    if(cancel.get()){ok=false;report.append("Przerwano test.\n");break;}
                    setProgress("Sprawdzanie: "+GuidedSession.motorLabel(address));
                    ModbusRtu.Reply id=io.transaction(address,4,1,0,450);
                    ModbusRtu.Reply h3=io.transaction(address,3,3,0,450);
                    ModbusRtu.Reply h4=io.transaction(address,3,4,0,450);
                    boolean match=id!=null && id.successful() && h3!=null && h3.successful()
                        && h4!=null && h4.successful() && GuidedSession.verificationMatches(address,id.value,h3.value,h4.value);
                    ok &= match;
                    report.append(GuidedSession.motorLabel(address)).append(match?": OK\n":": NIEPOTWIERDZONY (adres, h3, model lub CM)\n");
                }
                report.append("\nRAMKI RTU:\n").append(io.diagnostics());
            }catch(Exception e){ok=false;report.append("Błąd: ").append(e.getMessage());}
            final boolean success=ok;final String details=report.toString();
            handler.post(()->{setBusy(false);lastScanReport=details;readResult.setText(details);setInfo(success?"Wszystkie silniki potwierdzone.":"Nie potwierdzono całej centrali.");
                if (simpleWizard()) {
                    Intent result = new Intent(); result.putExtra("report", details);
                    setResult(success ? RESULT_OK : RESULT_CANCELED, result); finish(); return;
                }
                new AlertDialog.Builder(this).setTitle(success?"Test potwierdzony":"Test niepotwierdzony").setMessage(details.length()>2000?details.substring(0,2000):details)
                    .setPositiveButton("Wróć do kreatora",(d,w)->{Intent result=new Intent();result.putExtra("report",details);setResult(success?RESULT_OK:RESULT_CANCELED,result);finish();}).setCancelable(false).show();
            });
        });
    }
    @Override public void onBackPressed(){
        if(busy){cancel.set(true);setInfo("Poczekaj na zakończenie bieżącej operacji.");return;}
        if(guidedTarget()>0 || getIntent().hasExtra("verify_addresses")){Intent data=new Intent();data.putExtra("report",guidedAttemptHistory + lastScanReport);setResult(RESULT_CANCELED,data);}
        super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("guidedHistory", guidedAttemptHistory);
        state.putString("guidedReport", lastScanReport);
        super.onSaveInstanceState(state);
    }
    @Override public void onDestroy() {
        cancel.set(true);
        if(activityFan!=null)animateActivity(false);
        jobs.shutdownNow();
        try { unregisterReceiver(usbReceiver); } catch(Exception ignored) {}
        super.onDestroy();
    }
}
