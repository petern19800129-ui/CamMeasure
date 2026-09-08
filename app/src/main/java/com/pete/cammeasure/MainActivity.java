package com.pete.cammeasure;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_CONTACTS = 1001;
    private static final String PREFS = "unified_contact_prefs";
    private static final String KEY_COUNTRY_CODE = "country_code";

    private final List<ContactItem> allContacts = new ArrayList<>();
    private final List<ContactItem> visibleContacts = new ArrayList<>();
    private ContactAdapter adapter;
    private TextView statusView;
    private EditText searchView;
    private Button countryButton;
    private String countryCode = "27";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        countryCode = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_COUNTRY_CODE, "27");
        buildUi();
        ensureContactsPermission();
    }

    private void buildUi() {
        int pad = dp(10);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFFF7F7F7);

        TextView title = new TextView(this);
        title.setText("Unified Contact");
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(0xFF202020);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("T9 search · Call · SMS · WhatsApp");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF666666);
        subtitle.setPadding(0, 0, 0, dp(6));
        root.addView(subtitle);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);

        searchView = new EditText(this);
        searchView.setSingleLine(true);
        searchView.setHint("T9: 7383 = PETE");
        searchView.setTextSize(18);
        searchView.setInputType(InputType.TYPE_CLASS_PHONE);
        searchView.setShowSoftInputOnFocus(false);
        searchRow.addView(searchView, new LinearLayout.LayoutParams(0, dp(48), 1f));

        countryButton = new Button(this);
        countryButton.setText("+" + countryCode);
        countryButton.setAllCaps(false);
        countryButton.setOnClickListener(v -> editCountryCode());
        searchRow.addView(countryButton, new LinearLayout.LayoutParams(dp(78), dp(48)));
        root.addView(searchRow);

        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        keypad.setPadding(0, dp(4), 0, dp(2));
        addT9Row(keypad,
                new String[]{"1", "2\nABC", "3\nDEF"},
                new String[]{"1", "2", "3"});
        addT9Row(keypad,
                new String[]{"4\nGHI", "5\nJKL", "6\nMNO"},
                new String[]{"4", "5", "6"});
        addT9Row(keypad,
                new String[]{"7\nPQRS", "8\nTUV", "9\nWXYZ"},
                new String[]{"7", "8", "9"});

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = t9Button("CLEAR");
        clear.setOnClickListener(v -> searchView.setText(""));
        bottom.addView(clear, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button zero = t9Button("0\n+");
        zero.setOnClickListener(v -> appendT9("0"));
        bottom.addView(zero, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button back = t9Button("⌫");
        back.setTextSize(22);
        back.setOnClickListener(v -> backspaceT9());
        back.setOnLongClickListener(v -> {
            searchView.setText("");
            return true;
        });
        bottom.addView(back, new LinearLayout.LayoutParams(0, dp(44), 1f));
        keypad.addView(bottom);
        root.addView(keypad);

        statusView = new TextView(this);
        statusView.setText("Waiting for contacts permission…");
        statusView.setTextColor(0xFF666666);
        statusView.setTextSize(12);
        statusView.setPadding(0, dp(3), 0, dp(3));
        root.addView(statusView);

        ListView list = new ListView(this);
        list.setDividerHeight(1);
        adapter = new ContactAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContacts(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void addT9Row(LinearLayout keypad, String[] labels, String[] digits) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final String digit = digits[i];
            Button b = t9Button(labels[i]);
            b.setOnClickListener(v -> appendT9(digit));
            row.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        keypad.addView(row);
    }

    private Button t9Button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private void appendT9(String digit) {
        int start = Math.max(searchView.getSelectionStart(), 0);
        int end = Math.max(searchView.getSelectionEnd(), 0);
        Editable text = searchView.getText();
        text.replace(Math.min(start, end), Math.max(start, end), digit);
    }

    private void backspaceT9() {
        Editable text = searchView.getText();
        int start = Math.max(searchView.getSelectionStart(), 0);
        int end = Math.max(searchView.getSelectionEnd(), 0);
        if (start != end) {
            text.delete(Math.min(start, end), Math.max(start, end));
        } else if (start > 0) {
            text.delete(start - 1, start);
        }
    }

    private void ensureContactsPermission() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContacts();
            } else {
                statusView.setText("Contacts permission is required to show your contact list.");
            }
        }
    }

    private void loadContacts() {
        allContacts.clear();
        Set<String> seen = new HashSet<>();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (c != null) {
                int nameCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (c.moveToNext()) {
                    String name = c.getString(nameCol);
                    String number = c.getString(numberCol);
                    if (name == null || number == null || number.trim().isEmpty()) continue;
                    String key = name + "\u0000" + number;
                    if (seen.add(key)) allContacts.add(new ContactItem(name, number));
                }
            }
        } catch (Exception e) {
            statusView.setText("Could not read contacts: " + e.getMessage());
            return;
        }

        filterContacts(searchView.getText().toString());
    }

    private void filterContacts(String query) {
        visibleContacts.clear();
        String raw = query == null ? "" : query.trim();
        String qLower = raw.toLowerCase(Locale.ROOT);
        String qDigits = digitsOnly(raw);
        boolean numericT9 = !raw.isEmpty() && qDigits.length() == raw.length();

        for (ContactItem item : allContacts) {
            boolean match;
            if (raw.isEmpty()) {
                match = true;
            } else if (numericT9) {
                String numberDigits = digitsOnly(item.number);
                String nameDigits = nameToT9(item.name);
                match = numberDigits.contains(qDigits) || nameDigits.contains(qDigits);
            } else {
                match = item.name.toLowerCase(Locale.ROOT).contains(qLower)
                        || item.number.toLowerCase(Locale.ROOT).contains(qLower);
            }
            if (match) visibleContacts.add(item);
        }

        if (adapter != null) adapter.notifyDataSetChanged();
        if (statusView != null) {
            String mode = numericT9 && !raw.isEmpty() ? " · T9" : "";
            statusView.setText(visibleContacts.size() + " of " + allContacts.size() + " numbers" + mode);
        }
    }

    private static String nameToT9(String name) {
        if (name == null) return "";
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 'A' && c <= 'C') out.append('2');
            else if (c >= 'D' && c <= 'F') out.append('3');
            else if (c >= 'G' && c <= 'I') out.append('4');
            else if (c >= 'J' && c <= 'L') out.append('5');
            else if (c >= 'M' && c <= 'O') out.append('6');
            else if (c >= 'P' && c <= 'S') out.append('7');
            else if (c >= 'T' && c <= 'V') out.append('8');
            else if (c >= 'W' && c <= 'Z') out.append('9');
            else if (c >= '0' && c <= '9') out.append(c);
        }
        return out.toString();
    }

    private void editCountryCode() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setText(countryCode);
        input.setSelection(input.length());
        input.setHint("27");

        new AlertDialog.Builder(this)
                .setTitle("Default WhatsApp country code")
                .setMessage("Used for local numbers beginning with 0. South Africa = 27")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    String value = digitsOnly(input.getText().toString());
                    if (!value.isEmpty()) {
                        countryCode = value;
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putString(KEY_COUNTRY_CODE, countryCode).apply();
                        countryButton.setText("+" + countryCode);
                    }
                })
                .show();
    }

    private void dial(String number) {
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))));
    }

    private void sms(String number) {
        startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))));
    }

    private void whatsapp(String number, boolean callHint) {
        String waNumber = toWhatsAppNumber(number);
        if (waNumber.isEmpty()) {
            Toast.makeText(this, "Could not convert this number for WhatsApp", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = Uri.parse("https://wa.me/" + waNumber);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                intent.setPackage("com.whatsapp.w4b");
                startActivity(intent);
            } catch (ActivityNotFoundException e2) {
                intent.setPackage(null);
                startActivity(intent);
            }
        }

        if (callHint) {
            Toast.makeText(this,
                    "WhatsApp opens the contact; tap the phone icon to call.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private String toWhatsAppNumber(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        boolean international = trimmed.startsWith("+");
        String digits = digitsOnly(trimmed);
        if (digits.isEmpty()) return "";
        if (international) return digits;
        if (digits.startsWith("00") && digits.length() > 2) return digits.substring(2);
        if (digits.startsWith("0") && digits.length() > 1) return countryCode + digits.substring(1);
        return digits;
    }

    private static String digitsOnly(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9]", "");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Button actionButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(listener);
        return b;
    }

    private final class ContactAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleContacts.size(); }
        @Override public Object getItem(int position) { return visibleContacts.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ContactItem item = visibleContacts.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(8), dp(7), dp(8), dp(8));
            row.setBackgroundColor(0xFFFFFFFF);

            TextView name = new TextView(MainActivity.this);
            name.setText(item.name);
            name.setTextSize(18);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            name.setTextColor(0xFF202020);
            row.addView(name);

            TextView number = new TextView(MainActivity.this);
            number.setText(item.number);
            number.setTextSize(14);
            number.setTextColor(0xFF666666);
            number.setPadding(0, 0, 0, dp(3));
            row.addView(number);

            LinearLayout first = new LinearLayout(MainActivity.this);
            first.setOrientation(LinearLayout.HORIZONTAL);
            Button call = actionButton("Call", v -> dial(item.number));
            Button sms = actionButton("SMS", v -> sms(item.number));
            first.addView(call, new LinearLayout.LayoutParams(0, dp(42), 1f));
            first.addView(sms, new LinearLayout.LayoutParams(0, dp(42), 1f));
            row.addView(first);

            LinearLayout second = new LinearLayout(MainActivity.this);
            second.setOrientation(LinearLayout.HORIZONTAL);
            Button wa = actionButton("WhatsApp", v -> whatsapp(item.number, false));
            Button waCall = actionButton("WA Call", v -> whatsapp(item.number, true));
            second.addView(wa, new LinearLayout.LayoutParams(0, dp(42), 1f));
            second.addView(waCall, new LinearLayout.LayoutParams(0, dp(42), 1f));
            row.addView(second);

            return row;
        }
    }

    private static final class ContactItem {
        final String name;
        final String number;
        ContactItem(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }
}
