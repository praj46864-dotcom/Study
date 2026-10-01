# Ask Screen AI

Ek simple "share this screen, ask a question" assistant — jaise Gemini ka
on-demand screen-share feature. Pehle wale project se iska design jaan-bujhkar
alag hai:

| | Purana project | Ye app |
|---|---|---|
| Capture kab hota hai | Background me continuously (Accessibility Service) | Sirf jab user khud floating button dabaye |
| User ko pata chalta hai? | Chhupa hua | Android ka persistent "capture active" notification + hamesha visible floating button |
| Banking/gov apps | Chhupkar detect/block karta hai | Koi special-case logic nahi — kyunki background capture hi nahi hota |
| Data kahan jaata hai | Hardcoded/bundled keys se ek fixed server | Sirf aapki apni API key se, aapke chune hue endpoint par — ek sawaal, ek jawaab, kuch store nahi hota |

## Kaise kaam karta hai

1. App kholkar apni AI API key, base URL, aur model name save karein
   (default OpenAI-compatible chat-completions format use karta hai; agar
   aap Gemini ya koi aur vision-capable model ka OpenAI-compatible proxy
   use karte hain, bas URL/model badal dein).
2. "Start floating Ask AI button" dabayein.
3. Android khud aapse do permissions maangega:
   - **Display over other apps** (floating button dikhane ke liye)
   - **Screen recording ka ek-baar ka consent** (ye MediaProjection hai —
     Android isko use karte waqt hamesha ek chhota status-bar icon aur
     notification dikhata hai, poore session ke dauraan)
4. Ab jis bhi app/screen par ho, ek "Ask AI" button floating dikhega. Usse
   tap karein, apna sawaal likhein, "Ask" dabayein.
5. Sirf tab ek screenshot liya jaata hai, seedha aapki API key se seedha
   AI ko bheja jaata hai, aur jawaab turant usi panel me dikh jaata hai.
   Koi history, koi background logging, koi doosra server nahi.

## Build karna

Ye poora Android Studio project hai (Gradle + Kotlin). Isey build karne
ke liye:

1. Android Studio me "Open" karke is folder ko select karein.
2. Gradle sync hone dein (internet chahiye — is sandbox me nahi ho saka).
3. Run karein ya `./gradlew assembleDebug` se APK banayein.

## Zaroori limitations jo aapko pata hone chahiye

- MediaProjection consent **har naye session ke liye dobara maangi
  jaati hai** (Android ka hi rule hai) — ye jaan-bujhkar hai, taaki app
  silently background me kabhi capture na kar sake.
- Floating button ko hata nahi sakte jab tak capture active hai — ye
  bhi jaan-bujhkar hai, taaki user ko hamesha pata rahe ki capture ho
  sakta hai.
- API key is device par SharedPreferences me plain-text save hoti hai —
  production use ke liye isey Android Keystore se encrypt karna behtar
  rahega.
