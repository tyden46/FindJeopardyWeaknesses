# Jeopardy Weakness Trainer

A small Android app for drilling Jeopardy-style TSV questions.

## Expected TSV columns

Your input file should be named something like `filteredQuestions.tsv` and contain at least:

- `answer`
- `question`
- `category`

The app creates/uses a `correct` column in its saved working copy. When you mark a card:

- Correct = `1`
- Incorrect = `0`

## How it works

1. Install/open the app.
2. Tap **Import TSV** and choose `filteredQuestions.tsv`.
3. The app shows an item from the `answer` column.
4. Tap **Reveal** to show the `question` column.
5. Tap **Correct** or **Incorrect**.
6. The app saves your responses locally after every answer.
7. Tap **Export TSV** to write an updated `filteredQuestions.tsv` with the `correct` column.

The app chooses randomly from unanswered questions first. Once everything has a `correct` value, it chooses from all rows.

## Building an APK

Open this folder in Android Studio, then use:

**Build > Build Bundle(s) / APK(s) > Build APK(s)**

The generated APK will usually be at:

`app/build/outputs/apk/debug/app-debug.apk`

## Why export instead of direct in-place mutation?

Modern Android versions restrict direct writes to arbitrary files in Downloads. This app uses the Android file picker to import/export and keeps a local working copy so your progress is not lost.
