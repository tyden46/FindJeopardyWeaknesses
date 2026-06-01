package com.tyson.jeopardytrainer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT_TSV = 1001;
    private static final int REQ_EXPORT_TSV = 1002;

    private final ArrayList<String> headers = new ArrayList<>();
    private final ArrayList<String[]> rows = new ArrayList<>();
    private final Random random = new Random();

    private int answerCol = -1;
    private int questionCol = -1;
    private int categoryCol = -1;
    private int correctCol = -1;
    private int currentIndex = -1;

    private TextView statusText;
    private TextView categoryText;
    private TextView promptText;
    private TextView revealedText;
    private TextView statsText;
    private Button revealButton;
    private Button correctButton;
    private Button incorrectButton;
    private Button nextButton;
    private Button exportButton;

    private File workingFile;
    private volatile boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        workingFile = new File(getFilesDir(), "filteredQuestions_working.tsv");
        buildUi();

        if (workingFile.exists()) {
            try {
                loadFromInputStream(new FileInputStream(workingFile));
                status("Loaded saved working copy with " + rows.size() + " questions.");
                showRandomCard();
            } catch (Exception e) {
                status("Could not load saved working copy: " + e.getMessage());
                showImportPrompt();
            }
        } else {
            showImportPrompt();
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Jeopardy Weakness Trainer");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title, fullWidth());

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextColor(Color.DKGRAY);
        statusText.setPadding(0, 0, 0, dp(12));
        root.addView(statusText, fullWidth());

        LinearLayout topButtons = new LinearLayout(this);
        topButtons.setOrientation(LinearLayout.HORIZONTAL);
        topButtons.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(topButtons, fullWidth());

        Button importButton = new Button(this);
        importButton.setText("Import TSV");
        topButtons.addView(importButton, weightedButton());
        importButton.setOnClickListener(v -> openImportPicker());

        exportButton = new Button(this);
        exportButton.setText("Export TSV");
        exportButton.setEnabled(false);
        topButtons.addView(exportButton, weightedButton());
        exportButton.setOnClickListener(v -> openExportPicker());

        categoryText = new TextView(this);
        categoryText.setTextSize(16);
        categoryText.setTextColor(Color.rgb(80, 80, 80));
        categoryText.setPadding(0, dp(18), 0, dp(8));
        root.addView(categoryText, fullWidth());

        TextView answerLabel = new TextView(this);
        answerLabel.setText("Answer shown to you:");
        answerLabel.setTextSize(14);
        answerLabel.setTextColor(Color.GRAY);
        root.addView(answerLabel, fullWidth());

        promptText = new TextView(this);
        promptText.setTextSize(24);
        promptText.setTextColor(Color.BLACK);
        promptText.setPadding(0, dp(10), 0, dp(18));
        root.addView(promptText, fullWidth());

        revealButton = new Button(this);
        revealButton.setText("Reveal");
        root.addView(revealButton, fullWidth());
        revealButton.setOnClickListener(v -> revealCurrentQuestion());

        TextView questionLabel = new TextView(this);
        questionLabel.setText("Question / expected response:");
        questionLabel.setTextSize(14);
        questionLabel.setTextColor(Color.GRAY);
        questionLabel.setPadding(0, dp(18), 0, dp(4));
        root.addView(questionLabel, fullWidth());

        revealedText = new TextView(this);
        revealedText.setTextSize(22);
        revealedText.setTextColor(Color.rgb(20, 20, 20));
        revealedText.setPadding(0, dp(8), 0, dp(18));
        root.addView(revealedText, fullWidth());

        LinearLayout answerButtons = new LinearLayout(this);
        answerButtons.setOrientation(LinearLayout.HORIZONTAL);
        answerButtons.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(answerButtons, fullWidth());

        correctButton = new Button(this);
        correctButton.setText("Correct");
        correctButton.setTextColor(Color.WHITE);
        correctButton.setBackgroundColor(Color.rgb(21, 101, 192));
        answerButtons.addView(correctButton, weightedButton());
        correctButton.setOnClickListener(v -> markCurrent(1));

        incorrectButton = new Button(this);
        incorrectButton.setText("Incorrect");
        incorrectButton.setTextColor(Color.WHITE);
        incorrectButton.setBackgroundColor(Color.rgb(198, 40, 40));
        answerButtons.addView(incorrectButton, weightedButton());
        incorrectButton.setOnClickListener(v -> markCurrent(0));

        nextButton = new Button(this);
        nextButton.setText("Skip / Next Random");
        root.addView(nextButton, fullWidth());
        nextButton.setOnClickListener(v -> showRandomCard());

        statsText = new TextView(this);
        statsText.setTextSize(14);
        statsText.setTextColor(Color.DKGRAY);
        statsText.setPadding(0, dp(18), 0, dp(18));
        root.addView(statsText, fullWidth());

        setContentView(scrollView);
        setAnswerButtonsVisible(false);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void status(String message) {
        statusText.setText(message);
    }

    private void showImportPrompt() {
        status("Import filteredQuestions.tsv to begin. The app will create/use a `correct` column in its saved working copy, then you can export the updated TSV.");
        categoryText.setText("");
        promptText.setText("No TSV loaded yet.");
        revealedText.setText("");
        revealButton.setEnabled(false);
        correctButton.setEnabled(false);
        incorrectButton.setEnabled(false);
        nextButton.setEnabled(false);
        exportButton.setEnabled(false);
        statsText.setText("");
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_IMPORT_TSV);
    }

    private void openExportPicker() {
        if (rows.isEmpty()) {
            Toast.makeText(this, "No questions loaded.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/tab-separated-values");
        intent.putExtra(Intent.EXTRA_TITLE, "filteredQuestions.tsv");
        startActivityForResult(intent, REQ_EXPORT_TSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        if (requestCode == REQ_IMPORT_TSV) {
            try {
                final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
                // Not all providers allow persistable permissions.
            }
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) throw new Exception("Could not open selected file.");
                loadFromInputStream(inputStream);
                saveWorkingCopyAsync();
                status("Imported " + rows.size() + " questions. Answers are saved locally after each response.");
                showRandomCard();
            } catch (Exception e) {
                Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                status("Import failed: " + e.getMessage());
            }
        } else if (requestCode == REQ_EXPORT_TSV) {
            try {
                OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt");
                if (outputStream == null) throw new Exception("Could not open export destination.");
                writeTsv(outputStream);
                Toast.makeText(this, "Exported updated TSV.", Toast.LENGTH_SHORT).show();
                status("Exported updated TSV with `correct` column.");
            } catch (Exception e) {
                Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                status("Export failed: " + e.getMessage());
            }
        }
    }

    private void loadFromInputStream(InputStream inputStream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, n);
            }
        }

        List<List<String>> records = parseTsv(sb.toString());
        if (records.isEmpty()) {
            throw new Exception("TSV is empty.");
        }

        headers.clear();
        rows.clear();
        headers.addAll(records.get(0));

        answerCol = findColumn("answer");
        questionCol = findColumn("question");
        categoryCol = findColumn("category");
        correctCol = findColumn("correct");

        if (answerCol < 0) throw new Exception("Missing required column: answer");
        if (questionCol < 0) throw new Exception("Missing required column: question");
        if (categoryCol < 0) throw new Exception("Missing required column: category");

        if (correctCol < 0) {
            headers.add("correct");
            correctCol = headers.size() - 1;
        }

        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.size() == 1 && record.get(0).trim().isEmpty()) continue;
            rows.add(normalizeRow(record));
        }

        currentIndex = -1;
        exportButton.setEnabled(true);
        revealButton.setEnabled(true);
        nextButton.setEnabled(true);
    }

    private int findColumn(String name) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private String[] normalizeRow(List<String> record) {
        String[] row = new String[headers.size()];
        for (int i = 0; i < row.length; i++) {
            row[i] = i < record.size() ? record.get(i) : "";
        }
        return row;
    }

    private void showRandomCard() {
        if (rows.isEmpty()) {
            showImportPrompt();
            return;
        }

        ArrayList<Integer> unanswered = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String value = rows.get(i)[correctCol];
            if (value == null || value.trim().isEmpty()) unanswered.add(i);
        }

        if (!unanswered.isEmpty()) {
            currentIndex = unanswered.get(random.nextInt(unanswered.size()));
        } else {
            currentIndex = random.nextInt(rows.size());
        }

        String[] row = rows.get(currentIndex);
        String category = safe(row, categoryCol);
        String answer = safe(row, answerCol);

        categoryText.setText("Category: " + category);
        promptText.setText(answer);
        revealedText.setText("");
        revealButton.setEnabled(true);
        setAnswerButtonsVisible(false);
        updateStats();
    }

    private void revealCurrentQuestion() {
        if (currentIndex < 0 || currentIndex >= rows.size()) return;
        String[] row = rows.get(currentIndex);
        revealedText.setText(safe(row, questionCol));
        revealButton.setEnabled(false);
        setAnswerButtonsVisible(true);
    }

    private void setAnswerButtonsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        correctButton.setVisibility(visibility);
        incorrectButton.setVisibility(visibility);
        correctButton.setEnabled(visible);
        incorrectButton.setEnabled(visible);
    }

    private void markCurrent(int value) {
        if (currentIndex < 0 || currentIndex >= rows.size()) return;
        rows.get(currentIndex)[correctCol] = String.valueOf(value);
        saveWorkingCopyAsync();
        showRandomCard();
    }

    private String safe(String[] row, int col) {
        if (row == null || col < 0 || col >= row.length || row[col] == null) return "";
        return row[col];
    }

    private void updateStats() {
        int answered = 0;
        int correct = 0;
        Map<String, int[]> byCategory = new HashMap<>();

        for (String[] row : rows) {
            String v = safe(row, correctCol).trim();
            if (v.equals("1") || v.equals("0")) {
                answered++;
                if (v.equals("1")) correct++;
                String category = safe(row, categoryCol);
                int[] counts = byCategory.get(category);
                if (counts == null) {
                    counts = new int[]{0, 0};
                    byCategory.put(category, counts);
                }
                counts[0]++;
                if (v.equals("1")) counts[1]++;
            }
        }

        String currentCategory = currentIndex >= 0 ? safe(rows.get(currentIndex), categoryCol) : "";
        int[] catCounts = byCategory.get(currentCategory);
        String catSummary = "Current category answered: 0";
        if (catCounts != null && catCounts[0] > 0) {
            double catAcc = 100.0 * catCounts[1] / catCounts[0];
            catSummary = String.format(Locale.US, "Current category: %d/%d correct (%.1f%%)", catCounts[1], catCounts[0], catAcc);
        }

        double overall = answered > 0 ? 100.0 * correct / answered : 0.0;
        String summary = String.format(Locale.US,
                "Overall answered: %d/%d\nOverall correct: %d/%d (%.1f%%)\n%s\n\nTip: use Export TSV to write an updated filteredQuestions.tsv with the `correct` column.",
                answered, rows.size(), correct, answered, overall, catSummary);
        statsText.setText(summary);
    }

    private void saveWorkingCopyAsync() {
        if (saving) return;
        saving = true;
        new Thread(() -> {
            try {
                File temp = new File(getFilesDir(), "filteredQuestions_working.tmp");
                try (OutputStream os = new FileOutputStream(temp)) {
                    writeTsv(os);
                }
                if (workingFile.exists() && !workingFile.delete()) {
                    throw new Exception("Could not replace previous working copy.");
                }
                if (!temp.renameTo(workingFile)) {
                    throw new Exception("Could not save working copy.");
                }
            } catch (Exception e) {
                runOnUiThread(() -> status("Save failed: " + e.getMessage()));
            } finally {
                saving = false;
            }
        }).start();
    }

    private void writeTsv(OutputStream outputStream) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            writeRecord(writer, headers);
            for (String[] row : rows) {
                ArrayList<String> cells = new ArrayList<>();
                for (int i = 0; i < headers.size(); i++) {
                    cells.add(i < row.length ? row[i] : "");
                }
                writeRecord(writer, cells);
            }
        }
    }

    private void writeRecord(BufferedWriter writer, List<String> cells) throws Exception {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) writer.write('\t');
            writer.write(escapeTsv(cells.get(i)));
        }
        writer.write('\n');
    }

    private String escapeTsv(String value) {
        if (value == null) return "";
        boolean needsQuotes = value.contains("\t") || value.contains("\n") || value.contains("\r") || value.contains("\"");
        if (!needsQuotes) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private List<List<String>> parseTsv(String text) {
        ArrayList<List<String>> records = new ArrayList<>();
        ArrayList<String> currentRecord = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean inQuotes = false;

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        currentCell.append('"');
                        i += 2;
                        continue;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentCell.append(c);
                }
            } else {
                if (c == '"' && currentCell.length() == 0) {
                    inQuotes = true;
                } else if (c == '\t') {
                    currentRecord.add(currentCell.toString());
                    currentCell.setLength(0);
                } else if (c == '\n') {
                    currentRecord.add(currentCell.toString());
                    currentCell.setLength(0);
                    records.add(currentRecord);
                    currentRecord = new ArrayList<>();
                } else if (c == '\r') {
                    // Ignore CR; LF handles row ending. If CR-only files occur, they will still parse mostly fine.
                } else {
                    currentCell.append(c);
                }
            }
            i++;
        }

        if (currentCell.length() > 0 || !currentRecord.isEmpty()) {
            currentRecord.add(currentCell.toString());
            records.add(currentRecord);
        }
        return records;
    }
}
