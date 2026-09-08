# MillMitra (मिल मित्र) 🏭📄
### *The Intelligent Assistant for Paper Mill Production Managers*

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Language](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org/)
[![AI Engine](https://img.shields.io/badge/Gemini-Flash--Lite-orange.svg)](https://ai.google.dev/)
[![Status](https://img.shields.io/badge/Status-Production%20Ready-brightgreen.svg)]()

**MillMitra** is a native Android industrial assistant specifically engineered for **Paper Mill Production Managers** in India. It bridges the critical communication barrier between executive corporate management (conveying directives, SOPs, and chemical dosage orders in formal English) and on-the-floor paper machine shift operators (communicating in Hindi/Hinglish and specialized mill dialect).

---

## 🌟 Key Capabilities

### 1. 🔄 Decode: English ➔ Hindi (Shop-Floor Context)
- Converts complex, grammatically imperfect, or formal English messages from top management into clear, intuitive **Hindi**.
- **Crucial Feature:** Preserves core industrial terminology intact (e.g. couch roll, wire, press felt, calender, oiler, steam pressure, GSM, caliper, downtime, efiner).
- **Inline Vocabulary Explanations:** Explains difficult non-industrial English terms (e.g., *occurring* ➔ *हो रहा है*) directly inside a dedicated vocabulary card.

### 2. 🎙️ Voice Dictate: Hinglish ➔ Executive Shift Reports
- Production managers can speak naturally in **Hinglish** while walking the noisy plant floor.
- MillMitra cleans up the ambient speech and formats it into a crisp, structured **English Shift Handover Report** with bulleted sections:
  - *Current Status*
  - *Machine Section Impacted*
  - *Immediate Action Taken / Instructions*

### 3. 🎈 Ergonomic Floating Overlay & Ghost Mode
- Always accessible via a subtle floating ball on the side of the screen over **WhatsApp**, PDFs, spreadsheets, or machine monitoring screens.
- **Ghost Mode:** Automatically smoothly dims to **38% opacity after 4 seconds of idle** so it never obstructs reading plant messages.
- Instantly wakes up to 100% opacity on the slightest touch.

### 4. ⚡ Sub-Second Speed & Offline Plant Resilience
- **Lightning Fast:** Powered by gemini-flash-lite for **~800ms average response times**.
- **Embedded Offline Dictionary:** Deep inside paper mills (near basement pulpers, high-temperature boiler rooms, or corrugated iron roofs) where cellular connectivity drops, MillMitra automatically falls back to an embedded offline paper mill technical dictionary engine (MillOfflineTranslator).

### 5. 📲 1-Tap WhatsApp Dispatch
- Includes a dedicated one-click **WhatsApp Share** button in both the floating overlay and the 7-day logs, dispatching reports and decoded messages to team groups instantly without copy-paste friction.

---

## 🛠️ Architecture & Tech Stack

- **Framework:** Native Android (Kotlin)
- **Architecture:** Clean Architecture + MVVM (Repository Pattern)
- **Local Storage:** Room SQLite Database with automatic 7-day record pruning
- **Background Engine:** Android 14+ Compliant Foreground Service (specialUse) with WindowManager Overlay
- **Speech Engine:** Native Android SpeechRecognizer with package visibility queries
- **Design System:** Material Design 3, custom responsive card sizing (92% dynamic width), executive dark navy & emerald palette

---

## 📲 Installation & Quick Start

### Direct APK Install
1. Download the pre-compiled installer: **[MillMitra-latest.apk](MillMitra-latest.apk)** (~6.98 MB).
2. Install on any device running **Android 8.0 (API 26)** to **Android 15 (API 35)**.
3. Grant the **"Display over other apps"** permission.
4. Toggle the **Floating Assistant switch ON** and you're ready to go!

### Building from Source
\\\ash
git clone https://github.com/void053-hash/MillMitra.git
cd MillMitra
./gradlew assembleDebug
\\\

---

## 📄 License
Internal Production Release — Developed with ❤️ for Paper Mill Shift Operations.
