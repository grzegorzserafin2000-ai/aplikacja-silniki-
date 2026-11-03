package pl.cg.ziehlconfig;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Guided commissioning UI; all motor writes use the existing verified ZIEHL engine. */
public final class SetupActivity extends Activity {
  private LinearLayout root;
  private int step = -1, supply = 1, extract = 1, type = 0, current = 0;
  private String central = "", log = "";
  private int[] planned = new int[0];
  private boolean[] confirmed = new boolean[0];
  private boolean verified = false;
  private boolean awaitingSwitchOff = false;
  private static final int MOTOR = 20, VERIFY = 21, EXPORT = 22;
  private static final int ORANGE = 0xffff6900, GREY = 0xffaaaaaa;

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private GradientDrawable shape(int color) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(12));
    return g;
  }

  private void text(String value, int size) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(size >= 18 ? Color.WHITE : Visuals.MUTED);
    if (size >= 20) v.setTypeface(null, android.graphics.Typeface.BOLD);
    add(v);
  }

  private void add(View v) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(12);
    root.addView(v, lp);
  }

  private Button button(String title, Runnable action) {
    Button b = new Button(this);
    b.setText(title);
    b.setTextColor(
        new ColorStateList(
            new int[][] {new int[] {android.R.attr.state_pressed}, new int[] {}},
            new int[] {Color.BLACK, Color.WHITE}));
    b.setAllCaps(false);
    b.setPadding(dp(10), dp(12), dp(10), dp(12));
    StateListDrawable s = new StateListDrawable();
    s.addState(new int[] {android.R.attr.state_pressed}, shape(0xffffbc5e));
    s.addState(new int[] {}, shape(0xff292929));
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(ORANGE), s, null));
    Visuals.decorate(b);
    if (title.startsWith("✓")) {
      b.setBackground(
          new RippleDrawable(
              ColorStateList.valueOf(ORANGE),
              Visuals.panel(this, 0xff13251d, Visuals.GREEN),
              null));
    }
    b.setOnClickListener(v -> action.run());
    add(b);
    return b;
  }

  private Spinner spinner(String... values) {
    Spinner s = new Spinner(this);
    ArrayAdapter<String> a =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
    s.setAdapter(a);
    s.setBackgroundTintList(ColorStateList.valueOf(GREY));
    add(s);
    return s;
  }

  private EditText field(String value) {
    EditText e = new EditText(this);
    e.setTextColor(Color.WHITE);
    e.setSingleLine(true);
    e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    e.setText(value);
    add(e);
    return e;
  }

  private void message(String value) {
    new AlertDialog.Builder(this).setMessage(value).setPositiveButton("OK", null).show();
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.BLACK);
    getWindow().setNavigationBarColor(Color.BLACK);
    load();
    if (state != null) {
      step = state.getInt("step", -1);
      if (step >= 0) render();
      else menu();
    } else menu();
  }

  private void page(String heading) {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(Color.BLACK);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(20), dp(28), dp(20), dp(36));
    scroll.addView(root);
    setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener(
        (v, i) -> {
          scroll.setPadding(0, i.getSystemWindowInsetTop(), 0, i.getSystemWindowInsetBottom());
          return i;
        });
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.climagold_full);
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    root.addView(logo, new LinearLayout.LayoutParams(-1, dp(85)));
    text("Clima Gold Ziehl-Abegg", 22);
    text(heading, 21);
    if (step >= 0)
      add(
          Visuals.steps(
              this, new String[] {"Dane", "Silniki", "Przygotuj", "Koduj", "Test"}, step));
    Visuals.enter(root);
  }

  private void menu() {
    step = -1;
    page("Wybierz konfigurację");
    add(
        Visuals.tile(
            this,
            R.drawable.ui_unit,
            "Konfiguracja standardowa",
            "Krok po kroku: dane centrali, silniki i końcowy test.",
            () -> {
              if (planned.length > 0) resumeChoice();
              else {
                step = 0;
                render();
              }
            }));
    add(
        Visuals.tile(
            this,
            R.drawable.ui_tune,
            "Konfiguracja zaawansowana",
            "Wyszukiwanie, ręczne parametry i diagnostyka silnika.",
            () -> startActivity(new Intent(this, MainActivity.class))));
    text("ZIEHL-ABEGG • USB / RS485", 13);
  }

  private void resumeChoice() {
    new AlertDialog.Builder(this)
        .setTitle("Zapisana centrala " + central)
        .setItems(
            new String[] {"Wznów konfigurację", "Nowa centrala"},
            (d, n) -> {
              if (n == 0) {
                step = 3;
                render();
              } else
                new AlertDialog.Builder(this)
                    .setMessage(
                        "Rozpocząć nową sesję? Poprzedni postęp zostanie usunięty. Najpierw możesz"
                            + " wyświetlić lub zapisać raport.")
                    .setPositiveButton(
                        "Nowa sesja",
                        (x, w) -> {
                          planned = new int[0];
                          confirmed = new boolean[0];
                          central = "";
                          log = "";
                          verified = false;
                          current = 0;
                          awaitingSwitchOff = false;
                          step = 0;
                          save();
                          render();
                        })
                    .setNegativeButton("Anuluj", null)
                    .setNeutralButton("Wyświetl raport", (x, w) -> showReport())
                    .show();
            })
        .show();
  }

  private void render() {
    page("Konfiguracja standardowa — krok " + (step + 1) + " z 5");
    if (step == 0) {
      text("Dane centrali", 20);
      text("Oznaczenie centrali", 15);
      RadioGroup prefix = new RadioGroup(this);
      prefix.setOrientation(RadioGroup.HORIZONTAL);
      RadioButton c = new RadioButton(this);
      c.setId(View.generateViewId());
      c.setText("C");
      c.setTextColor(Color.WHITE);
      RadioButton k = new RadioButton(this);
      k.setId(View.generateViewId());
      k.setText("K");
      k.setTextColor(Color.WHITE);
      prefix.addView(c, new RadioGroup.LayoutParams(0, dp(56), 1));
      prefix.addView(k, new RadioGroup.LayoutParams(0, dp(56), 1));
      prefix.check(central.startsWith("K") ? k.getId() : c.getId());
      add(prefix);
      text("Numer centrali — same cyfry, np. 4234", 15);
      EditText number = field(central.length() > 1 ? central.substring(1) : "");
      number.setHint("Numer centrali — bez limitu długości");
      number.setHintTextColor(GREY);
      text("Typ centrali", 15);
      Spinner kind = spinner("Nawiewno-wywiewna", "Nawiewna", "Wywiewna");
      kind.setSelection(type);
      button(
          "Dalej",
          () -> {
            try {
              String nextCentral =
                  GuidedSession.centralNumber(
                      prefix.getCheckedRadioButtonId() == k.getId() ? "K" : "C",
                      number.getText().toString());
              int nextType = kind.getSelectedItemPosition();
              if (!central.equals(nextCentral) || type != nextType) {
                planned = new int[0];
                confirmed = new boolean[0];
                verified = false;
                current = 0;
                awaitingSwitchOff = false;
                log = "";
              }
              central = nextCentral;
              type = nextType;
              step = 1;
              save();
              render();
            } catch (IllegalArgumentException e) {
              number.setError(e.getMessage());
              number.requestFocus();
            }
          });
    } else if (step == 1) {
      text(central + " — liczba wentylatorów", 20);
      Spinner ns = null, ne = null;
      if (type != 2) {
        text("Nawiew", 16);
        ns = spinner("1", "2", "3", "4", "5", "6", "7", "8");
        ns.setSelection(Math.max(0, supply - 1));
      }
      if (type != 1) {
        text("Wywiew", 16);
        ne = spinner("1", "2", "3", "4", "5", "6", "7", "8");
        ne.setSelection(Math.max(0, extract - 1));
      }
      final Spinner s = ns, e = ne;
      button(
          "Dalej",
          () -> {
            supply = s == null ? 0 : s.getSelectedItemPosition() + 1;
            extract = e == null ? 0 : e.getSelectedItemPosition() + 1;
            int[] next = GuidedSession.addresses(supply, extract);
            if (!java.util.Arrays.equals(planned, next)) {
              planned = next;
              confirmed = new boolean[planned.length];
              current = 0;
              awaitingSwitchOff = false;
              verified = false;
            }
            step = 2;
            save();
            render();
          });
    } else if (step == 2) {
      text("Przygotuj centralę", 26);
      add(Visuals.connectionGuide(this));
      text("1. Zatrzymaj centralę.\n2. Podłącz adapter USB–RS485 do tabletu i przewodów centrali.\n3. Wyłącz zabezpieczenia wszystkich konfigurowanych wentylatorów.", 19);
      text("Aplikacja wskaże jeden wentylator naraz: najpierw nawiew, potem wywiew. Nie zmieniaj połączeń przy włączonym zasilaniu.", 17);
      button(
          "Instalacja przygotowana — dalej",
          () -> {
            step = 3;
            save();
            render();
          });
    } else if (step == 3) {
      if (planned.length == 0) { step = 1; render(); return; }
      if (!awaitingSwitchOff) current = GuidedSession.nextMotor(planned, confirmed);
      int complete = 0;
      for (boolean ok : confirmed) if (ok) complete++;
      text(central + " • gotowe " + complete + " z " + planned.length, 16);
      if (current >= planned.length) {
        text("Wszystkie wentylatory zakodowane", 26);
        text("Teraz sprawdzimy całą centralę.", 19);
        button("Przejdź do końcowego sprawdzenia", () -> { step = 4; save(); render(); });
      } else {
        int address = planned[current];
        add(Visuals.motorPlan(this, planned, confirmed, current));
        text((awaitingSwitchOff ? "✓ Gotowy: " : "Teraz: ") + GuidedSession.simpleLabel(address).toUpperCase(java.util.Locale.ROOT), 30);
        if (awaitingSwitchOff) {
          text("Ustawienia sprawdzone i potwierdzone.", 18);
          text("Wyłącz zabezpieczenie tego wentylatora. Pozostałe pozostaw wyłączone.", 23);
          button("Zabezpieczenie wyłączone — dalej", () -> {
            awaitingSwitchOff = false;
            current = GuidedSession.nextMotor(planned, confirmed);
            if (current == planned.length) step = 4;
            save(); render();
          });
        } else {
          text("Włącz zabezpieczenie tylko tego wentylatora. Wszystkie pozostałe muszą być wyłączone, a centrala zatrzymana.", 23);
          text("Po naciśnięciu przycisku aplikacja sama wyszuka wentylator, zapisze ustawienia i sprawdzi wynik.", 17);
          button("Wentylator włączony — rozpocznij", () -> launchMotor());
        }
      }
    } else {
      text(central + " — końcowe sprawdzenie", 20);
      text(
          "Włącz zabezpieczenia wszystkich wentylatorów. Następnie naciśnij przycisk poniżej — aplikacja sprawdzi je po kolei.",
          17);
      text(
          verified
              ? "✓ Wszystkie silniki potwierdzone w końcowym teście."
              : "Centrala nie ma jeszcze potwierdzonego końcowego testu.",
          18);
      button(
          "Włączone wszystkie — sprawdź",
          () -> {
            verified = false;
            save();
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("verify_addresses", planned);
            startActivityForResult(intent, VERIFY);
          });
      button("Wyświetl raport", () -> showReport());
      boolean canFinish = GuidedSession.canFinish(planned, confirmed, verified);
      Button finishButton = button("Zakończ procedurę", () -> finishProcedure());
      finishButton.setEnabled(canFinish);
      finishButton.setAlpha(canFinish ? 1f : .4f);
      if (!canFinish)
        text("Zakończenie będzie dostępne po poprawnym sprawdzeniu wszystkich wentylatorów.", 14);
    }
    if (step > 0 && step < 3)
      button(
          "Wstecz",
          () -> {
            step--;
            render();
          });
    button(
        "Menu główne",
        () -> {
          save();
          menu();
        });
  }

  private void finishProcedure() {
    if (!GuidedSession.canFinish(planned, confirmed, verified)) {
      message("Najpierw wykonaj poprawny końcowy test wszystkich wentylatorów.");
      return;
    }
    log +=
        "\n"
            + new java.util.Date()
            + " — PROCEDURA ZAKOŃCZONA. Wszystkie wentylatory potwierdzone.\n";
    save();
    step = -1;
    page("✓ Procedura zakończona");
    text(central, 20);
    text(
        "Kodowanie i końcowy test wszystkich wentylatorów zostały potwierdzone. Możesz wyświetlić"
            + " raport, zapisać go lub wrócić do menu.",
        17);
    button("Wyświetl raport", () -> showReport());
    button("Wróć do menu", () -> menu());
  }

  private void launchMotor() {
    confirmed[current] = false;
    verified = false;
    save();
    Intent i = new Intent(this, MainActivity.class);
    i.putExtra("guided_target", planned[current]);
    i.putExtra("guided_auto", true);
    startActivityForResult(i, MOTOR);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == EXPORT) {
      if (result == RESULT_OK && data != null && data.getData() != null)
        try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData())) {
          out.write(report().getBytes(StandardCharsets.UTF_8));
          message("Raport zapisany.");
        } catch (Exception e) {
          message("Nie zapisano raportu: " + e.getMessage());
        }
      return;
    }
    if (request == MOTOR || request == VERIFY) {
      step = request == MOTOR ? 3 : 4;
      if (data != null) {
        log += "\n" + new java.util.Date() + "\n" + data.getStringExtra("report") + "\n";
        if (result == RESULT_OK) {
          if (request == MOTOR && current < planned.length
              && data.getIntExtra("confirmed_address", -1) == planned[current]) {
            confirmed[current] = true;
            awaitingSwitchOff = true;
          }
          if (request == VERIFY) verified = true;
        }
      }
      save();
      render();
      if (request == VERIFY && result != RESULT_OK)
        message("Nie wszystkie wentylatory odpowiedziały poprawnie. Sprawdź ich zasilanie i przewody, następnie ponów sprawdzenie. Szczegóły zapisano w raporcie.");
    }
  }

  private String report() {
    StringBuilder b =
        new StringBuilder(
            "Clima Gold Ziehl-Abegg\nCentrala: "
                + central
                + "\nTyp: "
                + new String[] {"Nawiewno-wywiewna", "Nawiewna", "Wywiewna"}[type]
                + "\n");
    for (int i = 0; i < planned.length; i++)
      b.append(GuidedSession.motorLabel(planned[i]))
          .append(confirmed[i] ? ": zapis potwierdzony\n" : ": brak potwierdzenia\n");
    return b
        + "Końcowy test w tej sesji: "
        + (verified ? "POTWIERDZONY" : "NIEPOTWIERDZONY")
        + "\n"
        + log;
  }

  private void showReport() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(Color.BLACK);
    TextView content = new TextView(this);
    content.setText(report());
    content.setTextSize(16);
    content.setTextColor(Color.WHITE);
    content.setTextIsSelectable(true);
    content.setPadding(dp(20), dp(16), dp(20), dp(24));
    scroll.addView(content);
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("Raport centrali " + central)
        .setView(scroll)
        .setPositiveButton("Zamknij", null)
        .setNeutralButton("Zapisz jako TXT", (d, which) -> export())
        .create();
    dialog.show();
    if (dialog.getWindow() != null)
      dialog.getWindow().setLayout(
          WindowManager.LayoutParams.MATCH_PARENT,
          (int) (getResources().getDisplayMetrics().heightPixels * .85f));
  }

  private void export() {
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.setType("text/plain");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.putExtra(
        Intent.EXTRA_TITLE,
        "ClimaGold_"
            + (central.isEmpty() ? "raport" : central.substring(0, Math.min(64, central.length())))
            + ".txt");
    startActivityForResult(i, EXPORT);
  }

  @android.annotation.SuppressLint(
      "ApplySharedPref") // Persist the plan before launching motor programming.
  private void save() {
    try {
      JSONObject o = new JSONObject();
      o.put("central", central);
      o.put("type", type);
      o.put("supply", supply);
      o.put("extract", extract);
      o.put("current", current);
      o.put("awaitingSwitchOff", awaitingSwitchOff);
      o.put("log", log);
      JSONArray a = new JSONArray(), c = new JSONArray();
      for (int i = 0; i < planned.length; i++) {
        a.put(planned[i]);
        c.put(confirmed[i]);
      }
      o.put("addresses", a);
      o.put("confirmed", c);
      if (!getPreferences(0).edit().putString("session", o.toString()).commit())
        throw new IllegalStateException("Brak potwierdzenia zapisu pamięci.");
    } catch (Exception e) {
      message("Nie zapisano sesji: " + e.getMessage());
    }
  }

  private void load() {
    try {
      JSONObject o = new JSONObject(getPreferences(0).getString("session", "{}"));
      central = o.optString("central", "");
      type = o.optInt("type", 0);
      supply = o.optInt("supply", 1);
      extract = o.optInt("extract", 1);
      current = o.optInt("current", 0);
      awaitingSwitchOff = o.optBoolean("awaitingSwitchOff", false);
      log = o.optString("log", "");
      JSONArray a = o.optJSONArray("addresses"), c = o.optJSONArray("confirmed");
      if (a != null && c != null) {
        planned = new int[a.length()];
        confirmed = new boolean[a.length()];
        for (int i = 0; i < a.length(); i++) {
          planned[i] = a.getInt(i);
          confirmed[i] = c.getBoolean(i);
        }
      }
      if (current < 0 || current >= planned.length || !confirmed[current]) awaitingSwitchOff = false;
      verified = false;
    } catch (Exception e) {
      planned = new int[0];
      confirmed = new boolean[0];
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putInt("step", step);
    save();
  }

  @Override
  public void onBackPressed() {
    if (step < 0) super.onBackPressed();
    else {
      save();
      menu();
    }
  }
}
