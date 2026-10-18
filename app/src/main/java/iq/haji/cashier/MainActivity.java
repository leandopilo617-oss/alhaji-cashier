package iq.haji.cashier;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebView;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private final String PREFS = "haji_cashier";
    private final String KEY_CATEGORIES = "categories";
    private final String KEY_DISHES = "dishes";
    private final String KEY_ORDER = "current_order";
    private final String KEY_NUMBER = "latest_number";

    private LinearLayout root;
    private LinearLayout categoryList;
    private LinearLayout dishGrid;
    private LinearLayout orderRows;
    private TextView numberView;
    private TextView totalView;
    private EditText addressInput;

    private final ArrayList<String> categories = new ArrayList<>();
    private final ArrayList<JSONObject> dishes = new ArrayList<>();
    private final ArrayList<JSONObject> order = new ArrayList<>();

    private String selectedCategory = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshPhoneNumber();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadData();
        buildScreen();
        refreshPhoneNumber();
        requestCallScreeningRole();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
        refreshPhoneNumber();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshTask);
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBackgroundColor(Color.WHITE);
        return layout;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.BLACK);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(5), dp(5), dp(5), dp(5));
        return view;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.BLACK);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.WHITE)
        );
        return b;
    }

    private void buildScreen() {
        root = vertical();
        root.setPadding(dp(8), dp(6), dp(8), dp(6));

        TextView title = text("كاشير مطعم الحجي", 24);
        root.addView(title, new LinearLayout.LayoutParams(
                -1, dp(48)
        ));

        LinearLayout mainArea = horizontal();
        LinearLayout.LayoutParams mainParams =
                new LinearLayout.LayoutParams(-1, 0, 4f);
        root.addView(mainArea, mainParams);

        ScrollView dishesScroll = new ScrollView(this);
        dishGrid = vertical();
        dishesScroll.addView(dishGrid);

        LinearLayout.LayoutParams dishesParams =
                new LinearLayout.LayoutParams(0, -1, 5f);
        mainArea.addView(dishesScroll, dishesParams);

        ScrollView categoriesScroll = new ScrollView(this);
        categoryList = vertical();
        categoriesScroll.addView(categoryList);

        LinearLayout.LayoutParams categoryParams =
                new LinearLayout.LayoutParams(0, -1, 2f);
        mainArea.addView(categoriesScroll, categoryParams);

        LinearLayout orderArea = vertical();
        LinearLayout.LayoutParams orderParams =
                new LinearLayout.LayoutParams(-1, 0, 5f);
        root.addView(orderArea, orderParams);

        orderArea.addView(text("تفاصيل الطلب", 19));

        LinearLayout headings = horizontal();
        headings.addView(text("الاسم", 13),
                new LinearLayout.LayoutParams(0, dp(38), 3f));
        headings.addView(text("الكمية", 13),
                new LinearLayout.LayoutParams(0, dp(38), 2f));
        headings.addView(text("السعر", 13),
                new LinearLayout.LayoutParams(0, dp(38), 2f));
        headings.addView(text("الإجمالي", 13),
                new LinearLayout.LayoutParams(0, dp(38), 2f));
        orderArea.addView(headings);

        ScrollView orderScroll = new ScrollView(this);
        orderRows = vertical();
        orderScroll.addView(orderRows);
        orderArea.addView(orderScroll,
                new LinearLayout.LayoutParams(-1, 0, 2f));

        totalView = text("المجموع: 0 دينار", 18);
        orderArea.addView(totalView);

        LinearLayout phoneRow = horizontal();
        phoneRow.addView(text("رقم الزبون", 14),
                new LinearLayout.LayoutParams(dp(100), dp(48)));

        numberView = text("بانتظار اتصال", 16);
        phoneRow.addView(numberView,
                new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button clearNumber = button("مسح");
        clearNumber.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().remove(KEY_NUMBER).apply();
            refreshPhoneNumber();
        });
        phoneRow.addView(clearNumber);
        orderArea.addView(phoneRow);

        addressInput = new EditText(this);
        addressInput.setSingleLine(false);
        addressInput.setMinLines(1);
        addressInput.setMaxLines(2);
        addressInput.setTextSize(15);
        addressInput.setTextColor(Color.BLACK);
        addressInput.setHint("عنوان الزبون (اختياري)");
        orderArea.addView(addressInput,
                new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout actions = horizontal();

        Button printButton = button("طباعة الوصل");
        printButton.setOnClickListener(v -> printReceipt());

        Button newOrderButton = button("طلب جديد");
        newOrderButton.setOnClickListener(v -> clearOrder());

        Button exportButton = button("تصدير اليوم");
        exportButton.setOnClickListener(v -> exportData());

        actions.addView(printButton,
                new LinearLayout.LayoutParams(0, dp(50), 2f));
        actions.addView(newOrderButton,
                new LinearLayout.LayoutParams(0, dp(50), 1f));
        actions.addView(exportButton,
                new LinearLayout.LayoutParams(0, dp(50), 1f));

        orderArea.addView(actions);

        setContentView(root);

        renderCategories();
        renderDishes();
        renderOrder();
    }

    private void loadData() {
        android.content.SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        try {
            JSONArray savedCategories =
                    new JSONArray(prefs.getString(KEY_CATEGORIES, "[]"));

            for (int i = 0; i < savedCategories.length(); i++) {
                categories.add(savedCategories.getString(i));
            }

            JSONArray savedDishes =
                    new JSONArray(prefs.getString(KEY_DISHES, "[]"));

            for (int i = 0; i < savedDishes.length(); i++) {
                dishes.add(savedDishes.getJSONObject(i));
            }

            JSONArray savedOrder =
                    new JSONArray(prefs.getString(KEY_ORDER, "[]"));

            for (int i = 0; i < savedOrder.length(); i++) {
                order.add(savedOrder.getJSONObject(i));
            }
        } catch (Exception ignored) {
        }

        if (categories.isEmpty()) {
            categories.add("الوجبات");
            categories.add("المشويات");
            categories.add("المقبلات");
            categories.add("المشروبات");
        }

        selectedCategory = categories.get(0);
        saveData();
    }

    private void saveData() {
        try {
            JSONArray c = new JSONArray();
            for (String category : categories) {
                c.put(category);
            }

            JSONArray d = new JSONArray();
            for (JSONObject dish : dishes) {
                d.put(dish);
            }

            JSONArray o = new JSONArray();
            for (JSONObject item : order) {
                o.put(item);
            }

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CATEGORIES, c.toString())
                    .putString(KEY_DISHES, d.toString())
                    .putString(KEY_ORDER, o.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private void renderCategories() {
        categoryList.removeAllViews();

        Button add = button("+ إضافة قسم");
        add.setOnClickListener(v -> editCategory(-1));
        categoryList.addView(add);

        for (int i = 0; i < categories.size(); i++) {
            final int index = i;
            String name = categories.get(i);

            Button b = button(
                    name.equals(selectedCategory) ? "✓ " + name : name
            );

            b.setOnClickListener(v -> {
                selectedCategory = categories.get(index);
                renderCategories();
                renderDishes();
            });

            b.setOnLongClickListener(v -> {
                String[] options = {"تعديل القسم", "حذف القسم"};
                new AlertDialog.Builder(this)
                        .setTitle(name)
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                editCategory(index);
                            } else {
                                deleteCategory(index);
                            }
                        }).show();
                return true;
            });

            categoryList.addView(b);
        }
    }

    private void editCategory(int index) {
        EditText input = new EditText(this);
        input.setText(index >= 0 ? categories.get(index) : "");
        input.setHint("اسم القسم");

        new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "تعديل القسم" : "إضافة قسم")
                .setView(input)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;

                    if (index >= 0) {
                        String old = categories.set(index, name);

                        if (old.equals(selectedCategory)) {
                            selectedCategory = name;
                        }

                        for (JSONObject dish : dishes) {
                            if (old.equals(dish.optString("category"))) {
                                try {
                                    dish.put("category", name);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    } else {
                        categories.add(name);
                        selectedCategory = name;
                    }

                    saveData();
                    renderCategories();
                    renderDishes();
                }).show();
    }

    private void deleteCategory(int index) {
        String name = categories.get(index);

        new AlertDialog.Builder(this)
                .setTitle("حذف القسم")
                .setMessage("حذف القسم وأطباقه من المنيو؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (dialog, which) -> {
                    categories.remove(index);

                    for (int i = dishes.size() - 1; i >= 0; i--) {
                        if (name.equals(
                                dishes.get(i).optString("category"))) {
                            dishes.remove(i);
                        }
                    }

                    if (categories.isEmpty()) {
                        categories.add("الوجبات");
                    }

                    selectedCategory = categories.get(0);
                    saveData();
                    renderCategories();
                    renderDishes();
                }).show();
    }

    private void renderDishes() {
        dishGrid.removeAllViews();

        Button add = button("+ إضافة طبق للقسم: " + selectedCategory);
        add.setOnClickListener(v -> editDish(null));
        dishGrid.addView(add);

        LinearLayout row = null;
        int count = 0;

        for (JSONObject dish : dishes) {
            if (!selectedCategory.equals(dish.optString("category"))) {
                continue;
            }

            if (count % 3 == 0) {
                row = horizontal();
                dishGrid.addView(row);
            }

            JSONObject selectedDish = dish;
            String name = dish.optString("name");
            int price = dish.optInt("price", 0);

            Button b = button(name + "\n" + price + " د.ع");
            b.setMinHeight(dp(75));

            b.setOnClickListener(v -> addToOrder(selectedDish));

            b.setOnLongClickListener(v -> {
                String[] options = {"تعديل الطبق", "حذف الطبق"};
                new AlertDialog.Builder(this)
                        .setTitle(name)
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                editDish(selectedDish);
                            } else {
                                deleteDish(selectedDish);
                            }
                        }).show();
                return true;
            });

            row.addView(b,
                    new LinearLayout.LayoutParams(0, dp(85), 1f));
            count++;
        }

        if (count == 0) {
            dishGrid.addView(text("ماكو أطباق بهذا القسم", 15));
        }
    }

    private void editDish(JSONObject existing) {
        LinearLayout form = vertical();
        form.setPadding(dp(8), dp(4), dp(8), dp(4));

        EditText name = new EditText(this);
        name.setHint("اسم الطبق");
        name.setText(existing == null ? "" :
                existing.optString("name"));
        form.addView(name);

        EditText price = new EditText(this);
        price.setHint("السعر بالدينار");
        price.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
        );
        price.setText(existing == null ? "" :
                String.valueOf(existing.optInt("price")));
        form.addView(price);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "إضافة طبق" : "تعديل طبق")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String dishName = name.getText().toString().trim();
                    String priceText = price.getText().toString().trim();

                    if (dishName.isEmpty() || priceText.isEmpty()) {
                        Toast.makeText(this,
                                "اكتب اسم الطبق والسعر",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        int dishPrice = Integer.parseInt(priceText);
                        if (dishPrice < 0) return;

                        if (existing == null) {
                            JSONObject dish = new JSONObject();
                            dish.put("name", dishName);
                            dish.put("price", dishPrice);
                            dish.put("category", selectedCategory);
                            dishes.add(dish);
                        } else {
                            existing.put("name", dishName);
                            existing.put("price", dishPrice);
                        }

                        saveData();
                        renderDishes();
                    } catch (Exception ignored) {
                    }
                }).show();
    }

    private void deleteDish(JSONObject dish) {
        new AlertDialog.Builder(this)
                .setTitle("حذف الطبق")
                .setMessage("متأكد تريد تحذف هذا الطبق من المنيو؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (dialog, which) -> {
                    dishes.remove(dish);
                    saveData();
                    renderDishes();
                }).show();
    }

    private void addToOrder(JSONObject dish) {
        try {
            for (JSONObject item : order) {
                if (item.optString("name").equals(
                        dish.optString("name"))) {
                    item.put("qty", item.optInt("qty", 1) + 1);
                    saveData();
                    renderOrder();
                    return;
                }
            }

            JSONObject item = new JSONObject();
            item.put("name", dish.optString("name"));
            item.put("price", dish.optInt("price"));
            item.put("qty", 1);
            order.add(item);

            saveData();
            renderOrder();
        } catch (Exception ignored) {
        }
    }

    private void renderOrder() {
        if (orderRows == null || totalView == null) return;

        orderRows.removeAllViews();
        long total = 0;

        for (JSONObject item : order) {
            LinearLayout row = horizontal();

            String name = item.optString("name");
            int qty = item.optInt("qty", 1);
            int price = item.optInt("price", 0);
            long lineTotal = (long) qty * price;
            total += lineTotal;

            row.addView(text(name, 13),
                    new LinearLayout.LayoutParams(0, dp(44), 3f));

            LinearLayout qtyBox = horizontal();

            Button minus = button("-");
            minus.setOnClickListener(v -> changeQty(item, -1));
            qtyBox.addView(minus,
                    new LinearLayout.LayoutParams(0, dp(42), 1f));

            qtyBox.addView(text(String.valueOf(qty), 13),
                    new LinearLayout.LayoutParams(0, dp(42), 1f));

            Button plus = button("+");
            plus.setOnClickListener(v -> changeQty(item, 1));
            qtyBox.addView(plus,
                    new LinearLayout.LayoutParams(0, dp(42), 1f));

            row.addView(qtyBox,
                    new LinearLayout.LayoutParams(0, dp(44), 2f));

            row.addView(text(String.valueOf(price), 12),
                    new LinearLayout.LayoutParams(0, dp(44), 2f));

            row.addView(text(String.valueOf(lineTotal), 12),
                    new LinearLayout.LayoutParams(0, dp(44), 2f));

            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setMessage("حذف " + name + " من الطلب؟")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("حذف", (d, w) -> {
                            order.remove(item);
                            saveData();
                            renderOrder();
                        }).show();
                return true;
            });

            orderRows.addView(row);
        }

        totalView.setText("المجموع: " + total + " دينار");
    }

    private void changeQty(JSONObject item, int change) {
        int qty = item.optInt("qty", 1) + change;

        try {
            if (qty <= 0) {
                order.remove(item);
            } else {
                item.put("qty", qty);
            }

            saveData();
            renderOrder();
        } catch (Exception ignored) {
        }
    }

    private void refreshPhoneNumber() {
        if (numberView == null) return;

        String number = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_NUMBER, "");

        numberView.setText(
                number == null || number.isEmpty()
                        ? "بانتظار اتصال"
                        : number
        );
    }

    private void requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        RoleManager manager =
                (RoleManager) getSystemService(Context.ROLE_SERVICE);

        if (manager == null ||
                !manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this,
                    "الجهاز لا يدعم صلاحية استقبال رقم المتصل",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            new AlertDialog.Builder(this)
                    .setTitle("تفعيل رقم المتصل")
                    .setMessage(
                            "وافق على صلاحية تعريف المكالمات حتى يظهر رقم الزبون تلقائياً."
                    )
                    .setNegativeButton("لاحقاً", null)
                    .setPositiveButton("تفعيل", (dialog, which) -> {
                        Intent intent = manager.createRequestRoleIntent(
                                RoleManager.ROLE_CALL_SCREENING
                        );
                        startActivityForResult(intent, 42);
                    }).show();
        }
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void printReceipt() {
        if (order.isEmpty()) {
            Toast.makeText(this,
                    "أضف طبقاً للطلب أولاً",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String number = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_NUMBER, "");

        String address = addressInput.getText().toString().trim();

        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset='utf-8'>");
        html.append("<style>");
        html.append("body{font-family:sans-serif;text-align:center;}");
        html.append("table{width:100%;border-collapse:collapse;}");
        html.append("td,th{border-bottom:1px solid #000;padding:5px;}");
        html.append("</style></head><body dir='rtl'>");
        html.append("<h2>مطعم الحجي</h2>");
        html.append("<p>طلب سفري</p>");
        html.append("<p>رقم الطلب: ")
                .append(System.currentTimeMillis() % 100000)
                .append("</p>");
        html.append("<p>")
                .append(new SimpleDateFormat(
                        "yyyy/MM/dd  HH:mm",
                        Locale.getDefault()
                ).format(new Date()))
                .append("</p>");

        html.append("<p>رقم الزبون: ")
                .append(escapeHtml(number))
                .append("</p>");

        if (!address.isEmpty()) {
            html.append("<p>العنوان: ")
                    .append(escapeHtml(address))
                    .append("</p>");
        }

        html.append("<table><tr><th>الطبق</th><th>العدد</th>");
        html.append("<th>السعر</th><th>الإجمالي</th></tr>");

        long total = 0;

        for (JSONObject item : order) {
            int qty = item.optInt("qty", 1);
            int price = item.optInt("price", 0);
            long lineTotal = (long) qty * price;
            total += lineTotal;

            html.append("<tr><td>")
                    .append(escapeHtml(item.optString("name")))
                    .append("</td><td>").append(qty)
                    .append("</td><td>").append(price)
                    .append("</td><td>").append(lineTotal)
                    .append("</td></tr>");
        }

        html.append("</table><h3>المجموع: ")
                .append(total)
                .append(" دينار</h3>");
        html.append("<p>شكراً لزيارتكم</p></body></html>");

        PrintManager printManager =
                (PrintManager) getSystemService(Context.PRINT_SERVICE);

        if (printManager == null) {
            Toast.makeText(this,
                    "خدمة الطباعة غير متوفرة",
                    Toast.LENGTH_LONG).show();
            return;
        }

        WebView webView = new WebView(this);
        webView.loadDataWithBaseURL(
                null, html.toString(), "text/HTML", "UTF-8", null
        );

        webView.postDelayed(() -> {
            printManager.print(
                    "وصل مطعم الحجي",
                    webView.createPrintDocumentAdapter("وصل مطعم الحجي"),
                    new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build()
            );
        }, 700);
    }

    private void clearOrder() {
        new AlertDialog.Builder(this)
                .setTitle("طلب جديد")
                .setMessage("مسح الطلب الحالي وبدء طلب جديد؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("مسح", (dialog, which) -> {
                    order.clear();
                    addressInput.setText("");
                    saveData();
                    renderOrder();
                }).show();
    }

    private void exportData() {
        try {
            JSONArray allOrders = new JSONArray();
            JSONObject day = new JSONObject();

            day.put("date", new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
            ).format(new Date()));

            day.put("items", new JSONArray(
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                            .getString(KEY_ORDER, "[]")
            ));

            allOrders.put(day);

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_SUBJECT,
                    "بيانات كاشير مطعم الحجي");
            send.putExtra(Intent.EXTRA_TEXT,
                    allOrders.toString(2));

            startActivity(Intent.createChooser(
                    send, "تصدير بيانات الطلب الحالي"
            ));
        } catch (Exception e) {
            Toast.makeText(this,
                    "تعذر تجهيز البيانات",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
