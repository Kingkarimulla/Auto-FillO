# FormFill Pro - Universal Auto-Fill Assistant for Android

**FormFill Pro** is a complete, offline-first universal form filling assistant app built with Kotlin and Jetpack Compose. It overlays a floating action button (`SYSTEM_ALERT_WINDOW`) over web browsers and native apps, detecting input fields via the Android `AccessibilityService` and auto-filling saved profile data with 1-tap ease.

---

## 🌟 Key Features

1. **Floating Action Button Overlay**:
   - Draggable, floating overlay button that stays on top of all other apps.
   - Tap to expand a quick field paste popup overlay.

2. **Universal Field Detection**:
   - Uses `AccessibilityService` (`FormFillAccessibilityService`) to scan focused `EditText` and input nodes across browsers and apps.
   - Matches field hints, labels, and text against saved profile categories.

3. **100% Offline & AES-256 Encrypted**:
   - Sensitive user profile data (Aadhaar, PAN, Bank Accounts, Passports) is encrypted at rest using AES-256 encryption.
   - No internet required for core form detection or filling.

4. **Comprehensive Vault Data Categories**:
   - **Primary Details**: Name, DOB, Gender, Mobile, Email, Aadhaar/SSN, PAN, Passport
   - **Address Details**: Line 1/2, Landmark, City, State, Country, PIN Code
   - **Education Details**: Qualification, University, College, Year, Grade, Roll No
   - **Bank Details**: Holder Name, Bank Name, Account No, IFSC/SWIFT, Branch, UPI
   - **Employment Details**: Status, Company, Designation, Employee ID, Experience
   - **Family Details**: Father/Mother Name, Marital Status, Emergency Contacts
   - **Government & Official**: Category/Caste, Religion, Voter ID, Driving License, Vehicle Reg
   - **Custom Fields**: User defined Key-Value-Notes pairs

5. **In-App Interactive Demo Mode**:
   - Test 1-tap auto-filling on a built-in simulated candidate application portal without leaving the app!

---

## 🚀 How to Build & Run

### Prerequisites
- **Android Studio** Ladybug (2024.2.1+) or standard Gradle 8.x environment
- **Android SDK**: API 24+ (Android 7.0 Nougat or higher)
- **Kotlin**: 2.x

### Build Steps
1. Open project root directory in Android Studio.
2. Ensure Gradle sync completes automatically.
3. Build & Run on an Android device or emulator:
   ```bash
   ./gradlew assembleDebug
   ```

### Permissions Setup
When launched on device:
1. Grant **"Display over other apps"** (Overlay Permission) when prompted to activate the floating action button.
2. Enable **"FormFill Pro Accessibility Service"** in Android System Settings -> Accessibility -> Installed Services to allow input field scanning and 1-tap pasting into external apps.
