# Nabiz

Offline rhythm game. The music is synthesised on the fly and the song clock is
AudioTrack's playback head, so you are judged against what you actually hear.

- Deterministic charts: same seed, same piece — retries improve runs instead of re-rolling them.
- Tap latency calibration on first launch (8 taps against a metronome).
- Dark, warm timbre by design: sine fundamental + quiet 2nd partial, no harsh transients.
- No account, no ads, no network permission.

`./gradlew` is not required — GitHub Actions builds the APK.
