# Frenky CloudStream — Beta 18

## Beta 18: compatibilità HTML5 e diagnostica HTTP

- Supporto agli attributi HTML5 standard `src`, `data-src` e `data-file` per i video pubblicamente accessibili.
- Ispezione limitata degli iframe pubblici con `src` o `data-src` per player che usano caricamento differito.
- Il log ora indica il codice HTTP delle pagine non disponibili (per esempio 403 o 404), senza scambiarle per collegamenti video.
- Rimangono invariati catalogo delle serie, struttura delle stagioni e test del player già funzionanti.
- **Attenzione:** DeltaBit e Turbovid su *The Sentinel* non sono stati dimostrati riproducibili nella Beta 18. Il ramo `builds` viene aggiornato soltanto dopo compilazione riuscita. Non vengono aggirati controlli interattivi, DRM o accessi protetti.

# Frenky CloudStream — Beta 17

## Beta 17: lettori HTML5 pubblici e diagnosi

- Dopo un normale redirect HTTP riconosce direttamente le destinazioni MP4, HLS e DASH.
- Riconosce ulteriori sorgenti pubblicamente dichiarate: attributi `data-src` dei tag video, metadati video e URL non offuscati nelle configurazioni del lettore.
- Se la pagina pubblica non contiene un video diretto, esamina fino a due iframe HTML standard, senza eseguire JavaScript o seguire catene di iframe.
- Usa l'indirizzo della pagina del lettore incorporato come Referer quando serve a una sorgente HTML5 ordinaria.
- I log distinguono gli intermediari, le verifiche interattive, le pagine non raggiungibili e l'assenza di video HTML5.
- **DeltaBit/Clicka/SafeGo e Turbovid:** il log precedente indicava nessun flusso disponibile. Questo aggiornamento non garantisce che l'episodio si avvii: senza un URL multimediale pubblicamente accessibile, CloudStream mostrerà ancora «Nessun link trovato». Non aggira CAPTCHA, DRM, login o controlli di accesso.



## Beta 16: diagnosi The Sentinel (log Android del 10 ottobre)

- I messaggi `no supported extractor for safego.cc` e `turbovid.me` mostrano che per l'episodio provato non e stato ottenuto un flusso riproducibile: il lettore non e la causa iniziale.
- Il filtro nomi riconosce ora anche `Delta Bit`, `Delta-Bit` e `DeltaBit`; la presenza del nome non prova che il server sia effettivamente raggiungibile.
- Sono ammessi fino a tre redirect HTTP standard anche da SafeGo, senza elaborare pagine di verifica, CAPTCHA, JavaScript o limitazioni di accesso.
- Il log indica **nome visibile del server e dominio** per ogni tentativo, cosi da identificare a quale host corrisponde la voce Delta Bit di una puntata.
- La riproduzione di The Sentinel con Delta Bit resta **da verificare sul dispositivo**. La Beta 16 non aggiunge estrattori per host non supportati.


Repository CloudStream personale con **4 cataloghi ricercabili (titoli e schede informative)** e **6 domini monitorati**.

## Beta 15: confronto con Veezie e host esterni

- Eurostream usa ora l'indirizzo attivo `https://eurostreamings.yachts/` anche
  nella configurazione centrale, mantenendo `eurostream.pics` come riferimento storico.
- Gli URL intermedi sono seguiti **solo quando il server restituisce normali
  redirect HTTP** (massimo 3). Le pagine SafeGo che richiedono un codice o
  un'altra verifica non vengono saltate né completate automaticamente.
- Dove un player pubblico espone nel normale HTML un URL MP4/HLS/DASH in un
  elemento `video` o in una configurazione JavaScript non offuscata, il link
  può essere passato a CloudStream. Non viene eseguito JavaScript.
- **Non è stato verificato che DeltaBit per The Sentinel restituisca un flusso
  accessibile alla Beta 15.** Veezie possiede un proprio estrattore e non
  espone necessariamente lo stesso stream utilizzabile da CloudStream.
- Non vengono effettuati tentativi automatici di superamento di CAPTCHA,
  login, DRM, controlli di accesso o limitazioni del player.
- È necessario il test reale sul tablet e, se l'episodio non parte,
  verificare i log Android con tag `FrenkyCloudStream`.

## Beta 14: lettore HTML5 standard e MPEG-DASH

- Aggiunto il supporto a URL diretti **MP4, HLS (.m3u8) e DASH (.mpd)**.
- Quando nessun estrattore CloudStream ottiene un media, l'estensione può
  individuare tag HTML5 \`<video src>\` o \`<video><source src>\` presenti in una
  normale pagina pubblica accessibile senza interazione. Il video deve avere
  un URL HTTPS e un formato riconosciuto.
- Nessuna esecuzione di JavaScript, nessuna risoluzione di iframe nascosti,
  nessun tentativo di superare CAPTCHA o di accedere a stream protetti.
- I collegamenti \`clicka.cc\` e \`safego.cc\` non sono trattati come player:
  sono intermediari che possono richiedere verifiche.
- **The Sentinel / DeltaBit:** la sua riproduzione nativa NON è stata
  dimostrata. La presenza del video in Safari non prova che esista un flusso
  direttamente accessibile dalla repository. Questa versione aumenta la
  compatibilità con i player HTML5 standard ma non risolve necessariamente
  i server specifici mostrati nelle schermate.
- Conservati catalogo a quattro stagioni, metadati per episodio e altri provider.

## Beta 13: diagnosi per puntata e apertura nel browser

- Ogni episodio Eurostream mostra, dove l'interfaccia CloudStream espone la
  descrizione, i nomi dei server elencati nella pagina originale.
- Per collegamenti intermedi come `clicka.cc`, segnala che la riproduzione
  interna puo non essere disponibile senza completare verifiche nel browser.
- Il pulsante a forma di **globo in alto a destra**, gia presente nella schermata
  dettagli di CloudStream, apre la pagina originale della **serie**, dalla quale
  si sceglie manualmente stagione, episodio e server. **Non avvia automaticamente
  quella puntata nel player CloudStream.**
- L'estensione continua a tentare soltanto estrattori supportati e flussi HTTPS
  diretti; non inventa link multimediali, non aggira CAPTCHA/DRM e non apre
  automaticamente pagine pubblicitarie.
- Il test delle quattro stagioni e dei 65 episodi di *The Sentinel* e stato
  confermato sul tablet nella Beta 12; la riproduzione interna resta da testare
  soltanto con server compatibili e autorizzati.

## Beta 12: stagioni e diagnostica

- Il catalogo Eurostream conserva ora anche gli episodi privi di un link
  riconosciuto, evitando la sparizione silenziosa di intere stagioni.
- Cerca il blocco HTML con più stagioni e gestisce intestazioni suddivise
  tra elementi. Le quattro stagioni di The Sentinel richiedono un collaudo
  sul tablet: la schermata originale ne mostrava solo una.
- La scheda riporta il numero di puntate, stagioni e puntate con link elencati.
  Quelle senza server vengono contrassegnate.
- I fallimenti degli estrattori sono registrati in Android Logcat con il tag
  FrenkyCloudStream; non vengono registrati gli URL completi.
- I collegamenti che richiedono CAPTCHA, redirect protetti o accesso interattivo
  possono ancora mostrare "Nessun link trovato". Non si aggirano verifiche,
  DRM o controlli di accesso. Nessuna modifica ai provider demo o Blender.

## Stato delle fonti (10 ottobre 2026)

| Fonte | Ricerca titoli | Note |
|---|---|---|
| Eurostream | Codice integrato; URL di ricerca verificato | Ricerca WordPress `?s=...` |
| GuardaSerie | Codice integrato; URL di ricerca verificato | Ricerca DLE `do=search` |
| Il Genio dello Streaming | Codice integrato; URL di ricerca verificato | Ricerca DLE `do=search` |
| CB01 | Codice integrato; URL di ricerca verificato | Ricerca WordPress dalla radice del dominio |
| AltaDefinizione | Non ancora integrata | I percorsi testati restituivano la homepage |
| StreamingCommunity | Non ancora integrata | La ricerca richiede JavaScript |

**Importante:** la ricerca è stata verificata con sonde HTTP pubbliche. Non è stata ancora collaudata manualmente su Fire TV o tablet Android. La versione Beta 15 **non garantisce l'estrazione né la riproduzione dei film e delle puntate dai cataloghi di terzi**, e non garantisce che ogni ricerca trovi risultati. Nessuna autorizzazione alla distribuzione dei contenuti dei siti è stata verificata.

## Collaudo del lettore integrato (Beta 5)

La voce **Frenky - Test Player** contiene ora tre clip di prova indipendenti: **Big Buck Bunny - prova 10 secondi** (test-videos.co.uk), **Video alternativo - W3Schools** e **Flower - prova video MDN**. La versione precedente usava un vecchio file Google/GTV che può restituire HTTP 403 e causare l'errore ExoPlayer 2004. Apri uno dei video dalla scheda e premi **Riproduci film**; se un host risponde con un errore, prova un secondo video. Sono tutti test del player nativo, non un'integrazione di video dai sei cataloghi. I collegamenti sono documentati da fonti pubbliche ma devono essere verificati sul dispositivo finale.

L'esperimento è volutamente separato dai sei siti di catalogo: per questi ultimi **la ricerca rimane solo informativa** e la scritta «Prossimamente» persiste; non vengono ricavati link video né aggirati blocchi o DRM.

## Frenky Cinema Libero (Beta 9)

La Beta 6 conteneva collegamenti MP4 costruiti da UUID non verificati: per questo alcuni film, incluso Sintel, hanno restituito ERROR_CODE_IO_BAD_HTTP_STATUS (2004). La versione 7 ha eliminato **tutti** gli URL MP4 e gli UUID ipotizzati dal codice.

La sorgente comprende 14 titoli di opere Blender come catalogo potenziale. Quando premi Riproduci film, esegue una ricerca sull'API pubblica dell'istanza ufficiale PeerTube `video.blender.org`. Accetta solamente film dell'account `blender`, nel canale `Blender Open Movies`, con titolo compatibile e flussi HTTPS dichiarati dall'API. Supporta file MP4 e playlist M3U8 presenti nella risposta del server.

**La presenza del titolo nella schermata non garantisce che la copia video sia presente sul server.** Se la ricerca dell'API non trova un film disponibile, CloudStream non riceve alcun link. Anche i link ufficiali ottenuti dall'API dovranno essere verificati sul tablet.

### Miglioramenti Beta 9

- **Ricerca dei cataloghi più accurata**: normalizzazione degli articoli («The Sentinel»/«Sentinel»), dell'anno e delle varianti di punteggiatura; corrispondenza su parole complete, meno falsi positivi e tentativo di ricerca alternativa quando il titolo non compare.
- **Risultati delle serie riconosciuti correttamente** da CloudStream come serie, non film con tipo modificato.
- **Riproduzione dei film Blender**: selezione di una playlist HLS quando presente, più fino a quattro alternative MP4; tutte restano limitate all'API ufficiale. Nessun collegamento video viene ricavato dai siti di terzi.

- **Locandine e durata** ottenute dalle risposte dell'API pubblica di Blender quando presenti. Se il server non risponde, il titolo resta visualizzato senza poster.
- **Ricerca italiana** per titolo, anno e parole descrittive; la schermata iniziale conserva l'elenco dei film.
- **Qualità etichettata** (es. 720p MP4, 1080p MP4, Auto HLS) sulla base delle risoluzioni dichiarate dall'API, se disponibili. La selezione è gestita dai controlli di CloudStream.
- Due film aggiunti alla lista potenziale: **Charge** (2022) e **Wing It!** (2023), confermati nel catalogo ufficiale [Blender Studio](https://studio.blender.org/films/). Non è garantito che siano presenti anche su video.blender.org.
- **Motore di playback della v7 conservato**: nessuna modifica ai criteri che selezionano film e flussi. I collegamenti vengono sempre richiesti al server ufficiale prima di riprodurre.
- **Cache temporanea** per le informazioni grafiche; nessun account, API a pagamento o modifica alle altre estensioni.

La sorgente **Frenky - Test Player** già collaudata rimane intatta; Eurostream, CB01 e le altre fonti restano metadata-only, senza estrazione video.

## Nuovo (Beta 10): episodi realmente riproducibili con fonti autorizzate

La sorgente **Frenky - Episodi autorizzati** contiene un vero elenco di stagioni e puntate e implementa `loadLinks()` per flussi diretti MP4 e HLS. I link e i sottotitoli provengono dal registro GitHub [config/authorized-series.json](config/authorized-series.json), modificabile senza ricompilare (aggiornamento con cache di due minuti).

Nel registro iniziale sono presenti tre **clip dimostrative liberamente condivisibili** per verificare la selezione delle puntate e il player CloudStream. Per aggiungere una propria serie occorrono flussi con autorizzazione alla riproduzione e URL HTTPS diretti MP4 o M3U8. Supportati sottotitoli diretti SRT/VTT e più qualità per episodio.

**Limite:** Eurostreaming rimane una sorgente di schede informative. Nessun estrattore di video da servizi di terzi o bypass di accesso/DRM è stato integrato.

## Eurostream: stagioni, episodi e tentativo di riproduzione (Beta 11)

- Eurostream ha ora un provider dedicato, non il precedente provider di sole schede. Legge dalla pagina pubblica le stagioni (es. **STAGIONE 2 ITA**), i numeri (es. **2×03**) e i link dei relativi server; mantiene più link nello stesso episodio.
- Selezionando una puntata, `loadLinks()` chiede agli estrattori già disponibili in CloudStream di risolvere il relativo collegamento. I link HTTPS diretti MP4 e M3U8 sono supportati senza conversione.
- Non alimenta il player con pagine HTML spacciate per video; se un server non è compatibile o risponde 403 la riproduzione non viene promessa e l'episodio può non partire.
- Il caso osservato su Eurostream include collegamenti `clicka.cc` per player Turbovid e DeltaBit. **Non è stato possibile verificare l'estrazione effettiva di tali player (il collegamento web restituisce 403 nel test).** La compilazione corretta non dimostra che l'episodio si riproduca su Android.
- Nessuna modifica al motore di playback di Blender o alla sorgente demo autorizzata. Non sono implementati aggiramenti DRM o accessi protetti.

## Installazione

CloudStream → **Impostazioni → Estensioni → Aggiungi repository**.

Incolla:

```text
https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/main/repo.json
```

Installa la voce **MultiSource** (beta). La versione 15 rende le fonti selezionabili nel menu principale anche se non esiste ancora un catalogo iniziale; dopo aver scelto la sorgente usa **Cerca** per cercare titoli. La pagina iniziale resta volutamente vuota. Se era già installata, verifica di aver aggiornato a **versione 15** dopo il completamento della [build GitHub Actions](https://github.com/Frenk0371/frenky-cloudstream/actions/workflows/build.yml).

Manifest: [repo.json](repo.json) · [plugins.json](https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/builds/plugins.json) · [plugin compilato](https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/builds/MultiSource.cs3).

## Aggiornamento dei domini

- Sei URL registrati in [config/domains.json](config/domains.json).
- [GitHub Actions](.github/workflows/check-domains.yml) controlla i domini ogni giorno e registra il risultato in [reports/latest.json](reports/latest.json).
- Reindirizzamento HTTPS permanente (301/308) con stesso nome principale → aggiornamento centralizzato dopo i controlli previsti.
- Cambio di nome, redirect temporaneo, sito irraggiungibile → segnalazione e **verifica manuale**.
- Nessun sistema può identificare con certezza un nuovo dominio quando il precedente scompare senza reindirizzamenti verificabili.

L'estensione consulta la configurazione remota quando effettua una ricerca e usa l'URL originale in caso di errore di rete.

## Verifiche di sviluppo

Sonda dei percorsi di ricerca: [report](reports/catalogue_probe.json). Non è un test di riproduzione video.

```sh
python -m unittest discover -s tests -v
python tools/check_domains.py
python tools/probe_catalogues.py
gradle make makePluginsJson
```

Per sviluppare un sistema di riproduzione occorre usare soltanto flussi e fonti per cui esistano autorizzazioni adeguate, senza aggirare DRM o controlli di accesso.
