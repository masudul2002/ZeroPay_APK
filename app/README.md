# Zero Pay SMS Forwarder — Android Client (v1.0.0)

<p align="center">
  <img src="app/src/main/res/drawable/logo.png" alt="Zero Pay Logo" width="160" style="border-radius: 24px; box-shadow: 0 4px 20px rgba(0,0,0,0.1); background: white; padding: 12px;" />
</p>

<p align="center">
  <b>PAYMENTS WITHOUT LIMITS</b><br>
  Enterprise-Grade Automated SMS Ingestion & Real-Time Webhook Forwarding Engine for Android
</p>

<p align="center">
  <a href="https://github.com/masudul2002/ZeroPay-APK/releases/tag/v1.0.0"><img src="https://img.shields.io/badge/Release-v1.0.0-0284c7.svg?style=for-the-badge&logo=android" alt="Release v1.0.0"></a>
  <a href="https://github.com/masudul2002/ZeroPay-APK/releases/download/v1.0.0/ZeroPay-SMS-Forwarder-v1.0.0.apk"><img src="https://img.shields.io/badge/Download-APK%20(26MB)-22c55e.svg?style=for-the-badge&logo=googleplay" alt="Download APK"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge" alt="License: MIT"></a>
  <a href="https://github.com/masudul2002"><img src="https://img.shields.io/badge/Author-Masudul%20Hasan-orange.svg?style=for-the-badge&logo=github" alt="Author"></a>
</p>

---

## 📥 Direct Download & Release Asset

Download the latest production-ready Android APK directly:

- **Direct APK Download**: [ZeroPay-SMS-Forwarder-v1.0.0.apk](https://github.com/masudul2002/ZeroPay-APK/releases/download/v1.0.0/ZeroPay-SMS-Forwarder-v1.0.0.apk)
- **GitHub Release Page**: [v1.0.0 Release Notes](https://github.com/masudul2002/ZeroPay-APK/releases/tag/v1.0.0)
- **Repository Binary Path**: `release/ZeroPay-SMS-Forwarder-v1.0.0.apk`

---

## 🌟 Overview & Architecture

**Zero Pay SMS Forwarder** is the official Android companion application for the **Zero Pay — Centralized SaaS Payment Gateway (v1.4.0)** ecosystem.

Engineered specifically for automated Mobile Financial Service (MFS) transaction matching in Bangladesh (**bKash, Nagad, Rocket, Upay**), this app runs continuously in the background, intercepts bank and MFS incoming notifications, extracts payment parameters, and cryptographically dispatches them to the centralized Zero Pay SaaS backend via the dedicated `/api/webhooks/sms` endpoint.

```
┌─────────────────────────────────┐       ┌─────────────────────────────────┐
│       Android Device SIM        │       │   Zero Pay Gateway (Next.js)   │
│   (bKash / Nagad / Rocket SMS)  │       │          Backend Server         │
└────────────────┬────────────────┘       └────────────────┬────────────────┘
                 │                                         │
                 │ 1. Telephony SMS_RECEIVED               │
                 ▼                                         │
┌─────────────────────────────────┐                        │
│   SmsReceiver (BroadcastReceiver)│                        │
│   Priority: 999 + WakeLock      │                        │
└────────────────┬────────────────┘                        │
                 │                                         │
                 │ 2. Normalize & Parse TrxID              │
                 ▼                                         │
┌─────────────────────────────────┐                        │
│   Room Local SQLite Database    │                        │
│   Audit Log & Retry Queue       │                        │
└────────────────┬────────────────┘                        │
                 │                                         │
                 │ 3. HTTPS POST (Header: x-device-secret) │
                 └────────────────────────────────────────►│
                                                           │ POST /api/webhooks/sms
                                                           │ Match Intent & Verify
```

---

## 🔌 Webhook Protocol & Backend Integration

The client natively interfaces with the Zero Pay Backend `/api/webhooks/sms` endpoint:

### HTTP Headers
```http
POST /api/webhooks/sms HTTP/1.1
Host: your-zeropay-backend.com
Content-Type: application/json
x-device-secret: zp_sec_live_948f93b82e1a4cd8
User-Agent: ZeroPay-Android-Forwarder/1.0.0
```

### JSON Payload Schema
```json
{
  "sender": "bKash",
  "message": "You have received Tk 1,500.00 from 017XXXXXXXX. TrxID 9B8A7C6D5E at 24/09/2026 21:30. Fee Tk 0.00. Balance Tk 45,820.00.",
  "timestamp": 1727227800000,
  "deviceId": "zp_device_948f93b82e",
  "simSlot": 0
}
```

### Backend Verification Response
- `200 OK`: SMS acknowledged and transaction match queued.
- `401 Unauthorized`: Invalid or revoked `x-device-secret`.
- `422 Unprocessable Entity`: Malformed message payload.

---

## 🚀 Key Features

- **Fintech-Grade UI Design**: Built with Material Design 3 (M3) and Jetpack Compose, featuring crisp white-background branding consistent with premier MFS apps (bKash & Nagad).
- **Zero-Touch QR Onboarding**: Instant camera QR scanner that scans configuration payloads generated from the Zero Pay Merchant Dashboard.
- **Android 13+ / 14 / 15 Restricted Settings Support**: Built-in interactive guidance modal assisting users in bypassing Android 13+ sideload restrictions via "Allow restricted settings".
- **Highest Priority Background Receiver**: Implements `Telephony.SMS_RECEIVED` with priority `999` and Android WakeLock support for deep doze compatibility.
- **Full Offline Audit Trail**: Integrated Room Database persisting all incoming notifications, HTTP dispatch status, server latency, and delivery timestamps.
- **Real-Time Diagnostics**: In-app test webhook ping tool to verify network path, SSL handshakes, and secret authentication.

---

## 🛠️ Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.0+ (100% Coroutines & Flow) |
| **UI Framework** | Jetpack Compose + Material 3 (M3) |
| **Local Database** | Room SQLite Persistence with KSP |
| **Networking** | OkHttp 4.12 + Retrofit 2.11 + Moshi |
| **Hardware** | CameraX 1.4 + ZXing QR Core |
| **Security** | AndroidX Security Crypto & EncryptedSharedPreferences |
| **Target SDK** | Android 14 / 15 (API 34 / 36), minSdk 24 (Android 7.0+) |

---

## 📦 Building from Source

```bash
# Clone the repository
git clone https://github.com/masudul2002/ZeroPay-APK.git
cd ZeroPay-APK

# Compile and assemble production APK
./gradlew :app:assembleDebug

# The built binary will be located at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 👨‍💻 Author & Maintainer

<p align="center">
  <b>MD. MASUDUL HASAN</b><br>
  Software Engineer & Student, Department of Computer Science & Engineering (CSE)<br>
  <b>Sunamgonj Science and Technology University (SSTU)</b>
</p>

> *“মিথ্যা একটি রোগ এবং সত্যবাদিতা একটি নিরাময়‼️ / Lying is a disease and truthfulness is a cure.🌸☘️”*

- **Founder & Lead**: Infinity Tech
- **Location**: Huzrapur, Sapahar 6560, Naogaon, Rajshahi, Bangladesh
- **Website**: [https://www.masudulhasan.me](https://www.masudulhasan.me)
- **GitHub**: [@masudul2002](https://github.com/masudul2002)
- **Email**: [23240442@sstu.ac.bd](mailto:23240442@sstu.ac.bd)
- **LinkedIn**: [in/masudul2002](https://www.linkedin.com/in/masudul2002)
- **Codeforces**: [MASUDUL2002](https://codeforces.com/profile/MASUDUL2002)
- **Facebook**: [masudul2002](https://www.facebook.com/masudul2002)

---

## 📄 License & Copyright

Copyright (c) 2026 **MD. MASUDUL HASAN (Infinity Tech)**.  
Licensed under the [MIT License](LICENSE).
