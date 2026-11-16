# PotuzhnoGram — development plan

This file records the agreed product scope for the PotuzhnoGram Android client.
Implementation work must be done on `potuzhnogram-development`; keep `master` unchanged.

## Product identity and interface

- App name: PotuzhnoGram.
- Main screen: a custom compact chat list, not a copy of Telegram's default layout.
- Visual direction: dark Terminal-inspired interface with cool blue/silver accents; no decorative animations.
- Header: POTUZHNOGRAM branding and a technical connection indicator.
- One combined chat feed; do not add separate All / Personal / Groups / Channels / Bots tabs.
- Each chat row keeps the standard avatar and shows name, last message, time, and unread count.
- Search gets a custom Terminal-style surface while retaining search across chats, messages, people, groups, and channels.
- New-chat control gets a compact custom appearance; the existing create-contact/group/channel flows remain functional.
- Launch screen follows the previously agreed dark, mysterious creator concept.

## Keep working as in Telegram for now

- Authentication, account/session handling, MTProto networking, message sending and receiving.
- Chat view, message bubbles, composer, attachments, calls, contacts, profiles, notifications.
- Standard user and chat avatars, profile-photo/name editing, QR codes, contact management and sync.
- Side menu and settings remain standard for the first implementation.
- Ukrainian, Russian, and English interface localizations remain available.

## Remove or hide from the client interface

- Telegram Premium entry points and Premium-only client interface/features.
- Telegram Business interface/features.
- Telegram Wallet and bank-card/payment-card interface.
- Sponsored/ad placements in the client UI.

These changes must be scoped carefully. Do not break core messaging, account state, Stars, or Gifts. Do not attempt to cancel or alter a user's server-side subscription or account state.

## Explicitly preserve

- Telegram Gifts: browsing, sending, receiving, and displaying gifts.
- Telegram Stars: existing Stars balance and Stars-powered gift flows.
- Existing standard messaging and account functionality.

## Implementation order

1. Map navigation, main-screen, search, Premium, Business, Wallet, and sponsored-message entry points.
2. Identify shared dependencies before changing or removing any code.
3. Implement PotuzhnoGram branding and the custom main-screen/search presentation.
4. Hide/remove Premium, Business, Wallet/card, and ad UI without deleting shared Stars/Gifts or messaging code.
5. Build the Android app and resolve compile/runtime regressions.
6. Test login, chats, search, contacts, calls, notifications, Gifts, and Stars before preparing an APK.

## Safety rule

Prefer removing navigation and UI entry points first. Delete implementation classes or dependencies only after confirming they are not used by retained features. Never claim a feature is fully removed until code inspection and app testing verify it.
