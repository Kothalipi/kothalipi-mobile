# iPhone (iOS)-এ কথালিপি মোবাইল ইনস্টল করার গাইড

অ্যাপ স্টোরে রিলিজ হওয়ার আগ পর্যন্ত আপনি নিচের যেকোনো একটি পদ্ধতিতে আপনার iPhone-এ **Kothalipi Mobile (`.ipa`)** ইনস্টল করে ব্যবহার করতে পারবেন।

---

## পদ্ধতি ১: AltStore বা SideStore দিয়ে ইনস্টল (সবচেয়ে সহজ, Mac বা Windows)

1. আপনার কম্পিউটারে [AltServer](https://altstore.io/) বা [SideStore](https://sidestore.io/) ইনস্টল করে আপনার iPhone-এ AltStore/SideStore সেটআপ করুন।
2. GitHub Releases পেজ থেকে লেটেস্ট `Kothalipi-v*.ipa` ফাইলটি iPhone-এ ডাউনলোড করুন।
3. **AltStore / SideStore** অ্যাপ ওপেন করুন → **My Apps** → উপরের **`+`** বাটনে ট্যাপ করুন → ডাউনলোড করা `Kothalipi-*.ipa` ফাইলটি সিলেক্ট করুন।
4. ইনস্টল হয়ে গেলে iPhone-এ যান:
   `Settings → General → VPN & Device Management → [আপনার Apple ID] → Trust`।

> **টিপস:** ফ্রি Apple ID দিয়ে সাইন করলে প্রতি ৭ দিন পরপর Wi-Fi-তে অটো-রিফ্রেশ হয়ে যাবে (SideStore হলে কম্পিউটার ছাড়াই ফোন থেকে রিফ্রেশ করা যায়)।

---

## পদ্ধতি ২: Sideloadly দিয়ে ইনস্টল (Windows / macOS)

1. [Sideloadly](https://sideloadly.io/) ডাউনলোড ও ওপেন করুন।
2. iPhone-টি USB কেবল দিয়ে কানেক্ট করুন।
3. `Kothalipi-*.ipa` ফাইলটি Sideloadly-তে ড্র্যাগ-অ্যান্ড-ড্রপ করুন।
4. আপনার Apple ID দিয়ে **Start** ক্লিক করুন।
5. ইনস্টল শেষ হলে `Settings → General → VPN & Device Management → Trust` করুন।

---

## পদ্ধতি ৩: Mac + Xcode দিয়ে সরাসরি ইনস্টল (ডেভেলপারদের জন্য)

```bash
git clone https://github.com/Kothalipi/kothalipi-mobile.git
cd kothalipi-mobile/ios
xcodegen generate
open Kothalipi.xcodeproj
```

1. Xcode-এ `Kothalipi` এবং `KothalipiKeyboard` — দুটি টার্গেটের **Signing & Capabilities**-এ আপনার নিজের Apple ID (Personal Team) সিলেক্ট করুন এবং Bundle ID ইউনিক করুন (যেমন `com.yourname.kothalipi`)।
2. আপনার iPhone সিলেক্ট করে **Run (`Cmd + R`)** চাপুন।

---

## ইনস্টল করার পর গুরুত্বপূর্ণ ৩টি সেটআপ

1. **API Key সেট করুন:** কথালিপি অ্যাপ ওপেন করে `⚙️ Settings`-এ যান → ফ্রি **Google Gemini API Key** (অথবা **Groq / OpenAI Key**) দিয়ে **Test API Key** ও **Save** চাপুন।
2. **iOS ভয়েস কীবোর্ড অন করুন:**
   - `iPhone Settings → General → Keyboard → Keyboards → Add New Keyboard… → Kothalipi`
   - এরপর `Kothalipi` কীবোর্ডে ট্যাপ করে **`Allow Full Access`** অন করুন (মাইক্রোফোন ও AI কলের জন্য এটি প্রয়োজন)।
3. **Action Button / Siri Shortcut (iPhone 15 Pro / 16+):**
   - `Settings → Action Button → Shortcut → Dictate with Kothalipi` সিলেক্ট করলে এক চাপে ভয়েস টাইপিং চালু হবে!
