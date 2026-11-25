# NereusDuel 1.0.0

Duel 1v1 **best-of-three** di arena. Dua mode:
- **Kit Adil** — semua pemain memakai kit yang sama (diatur di `config.yml`, bawaan: Kit Besi dan Kit Berlian). Kit hilang otomatis setelah duel.
- **Armor Sendiri** — memakai armor/senjata milik sendiri. Setiap ronde, perlengkapan di-reset ke keadaan awal sehingga makanan/panah/durability yang terpakai kembali.

Inventory asli (armor, offhand, XP, HP, posisi, efek) **disimpan ke file sebelum duel dan dikembalikan persis setelahnya**. Tidak ada kematian sungguhan: serangan yang akan mematikan dibatalkan dan dihitung sebagai ronde kalah, jadi tidak ada drop dan tidak ada kehilangan item.

## Pasang
Upload jar ke `plugins/`, restart. Tidak ada dependency lain.

## Setup arena (di game, sebagai OP)
```
/duel admin arena create arena1
/duel admin arena setspawn arena1 1    (berdiri di titik spawn pemain 1)
/duel admin arena setspawn arena1 2    (berdiri di titik spawn pemain 2)
```
Arena siap bila kedua titik ada di dunia yang sama. Buat beberapa arena agar beberapa duel bisa berjalan bersamaan. Buat arena tertutup (dinding/penghalang) — keluar dari `match.arena-radius` atau jatuh dalam `match.void-depth` = kalah ronde.

## Perintah pemain
- `/duel` menu (pilih Armor Sendiri / kit; Bedrock friendly), alias `/pvp`, `/dd`
- `/duel queue [own|<kit>]`, `/duel leave` (keluar antrean / menyerah)
- `/duel challenge <pemain> [own|<kit>]`, `/duel accept`, `/duel deny`
- `/duel stats [pemain]`, `/duel top`
- Admin: `/duel admin arena create|setspawn|remove|list`, `forceend <pemain>`, `reload`

Tip: pasang NPC atau sign di lobby yang menjalankan `/duel`.

## Aturan duel
Skor ronde diperlukan `match.rounds-to-win` (default 2 = best of three). Hitung mundur 5 detik (tidak bisa bergerak), batas waktu ronde 120 detik (HP lebih banyak menang, sama = ulang). Selama duel: tidak bisa drop/ambil item, bangun/hancurkan blok, buka container, ender pearl, teleport, dan perintah selain daftar `match.allowed-commands`. Hanya lawan yang bisa melukai. Keluar server saat duel = menyerah, inventory dipulihkan.

## Keamanan data
Snapshot ada di `snapshots/<uuid>.yml`; bila server crash saat duel, pemain dipulihkan saat login berikutnya. Hadiah menang/kalah (perintah konsol) bisa diatur di `match.winner-commands`/`loser-commands` (kosong secara bawaan agar tidak bisa di-farm).

## Catatan jujur
- Belum diuji di server nyata (dikompilasi oleh CI). Uji dulu dengan dua akun.
- Pemain yang sedang punya efek/XP/gamemode khusus dipulihkan sebisanya; plugin lain yang mengubah inventory (mis. kosmetik) tidak diketahui plugin ini.
- Tidak ada mode penonton dan tidak ada ELO (kemungkinan update berikutnya).
