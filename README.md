# Nereus Project

Custom Paper 1.21.1 plugins (Java 21) by naook1/adityap.

- **NereusRealm** (2.0, sudah termasuk gadget hotbar dan menu teleport) - `/nereus` (alias `/nsi`): SATU menu untuk semua:
  RTP, Survival, Gacha, Spawn, Daily, Kits, AFK Center, Redeem, Join/Leave style + custom message, Rank, Rating,
  Info & Rules, Settings. Item gadget di hotbar slot 9 membuka menu yang sama. Small caps.

- **NereusLottery** - `/lottery`: undian terjadwal (WITA), tiket, jackpot yang menumpuk, menu GUI, tanpa pajak.
  Butuh Vault + plugin ekonomi (EssentialsX).

- **NereusFarmPrice** - `/farmprice`: Daily Farm Boost: 3 farm mendapat bonus harga jual +50% tiap 24 jam (rotasi otomatis),
  harga dasar dibaca dari EconomyShopGUI, tampilan teks (chat/buku).

- **NereusBarter** - `/barter <player>`: barter item + uang antar 2 pemain, GUI bersama, konfirmasi 2 tahap,
  anti-tipu (status Siap reset kalau penawaran berubah), syarat playtime 20 menit dan jarak 30 blok.

- **NereusLand** - bossbar nama land yang disinkronkan langsung dengan RedProtect (`/landbar` untuk on/off).
- **NereusHide** - `/hide`: petak umpet, pencari diundi acak, sistem poin (temuan +1, ketemu -5 dst), admin tidak ikut
- **NereusFishing** - `/fish`: joran bertingkat (bisa pakai model custom), ikan custom berdasarkan rarity, berat acak, rekor `/fish top`, jual `/fishsell` via Vault
- **NereusIcons** - icon rank custom (font pack Java + glyph pack Bedrock), ganti `:owner:`/`:rank:` di prefix chat, placeholder PAPI `%nereusicons_rank%` untuk TAB
  kecuali `/hide join`, menu admin `/hide admin`, inventory pemain disimpan aman ke file.

Build: buka tab Actions, jalankan "Build" terbaru, unduh artifact `plugins` (berisi 6 jar).
