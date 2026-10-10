# Frenky CloudStream — Beta 10

Repository CloudStream personale con **4 cataloghi ricercabili (titoli e schede informative)** e **6 domini monitorati**.

## Stato delle fonti (10 ottobre 2026)

| Fonte | Ricerca titoli | Note |
|---|---|---|
| Eurostream | Codice integrato; URL di ricerca verificato | Ricerca WordPress `?s=...` |
| GuardaSerie | Codice integrato; URL di ricerca verificato | Ricerca DLE `do=search` |
| Il Genio dello Streaming | Codice integrato; URL di ricerca verificato | Ricerca DLE `do=search` |
| CB01 | Codice integrato; URL di ricerca verificato | Ricerca WordPress dalla radice del dominio |
| AltaDefinizione | Non ancora integrata | I percorsi testati restituivano la homepage |
| StreamingCommunity | Non ancora integrata | La ricerca richiede JavaScript |

**Importante:** la ricerca è stata verificata con sonde HTTP pubbliche. Non è stata ancora collaudata manualmente su Fire TV o tablet Android. La versione Beta 10 **non implementa l'estrazione né la riproduzione dei film e delle puntate dai cataloghi di terzi**, e non garantisce che ogni ricerca trovi risultati. Nessuna autorizzazione alla distribuzione dei contenuti dei siti è stata verificata.

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

## Installazione

CloudStream → **Impostazioni → Estensioni → Aggiungi repository**.

Incolla:

```text
https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/main/repo.json
```

Installa la voce **MultiSource** (beta). La versione 10 rende le fonti selezionabili nel menu principale anche se non esiste ancora un catalogo iniziale; dopo aver scelto la sorgente usa **Cerca** per cercare titoli. La pagina iniziale resta volutamente vuota. Se era già installata, verifica di aver aggiornato a **versione 10** dopo il completamento della [build GitHub Actions](https://github.com/Frenk0371/frenky-cloudstream/actions/workflows/build.yml).

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
