# Frenky CloudStream — Beta 5

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

**Importante:** la ricerca è stata verificata con sonde HTTP pubbliche. Non è stata ancora collaudata manualmente su Fire TV o tablet Android. La versione Beta 5 **non implementa l'estrazione né la riproduzione dei film e delle puntate dai cataloghi di terzi**, e non garantisce che ogni ricerca trovi risultati. Nessuna autorizzazione alla distribuzione dei contenuti dei siti è stata verificata.

## Collaudo del lettore integrato (Beta 5)

La voce **Frenky - Test Player** contiene ora tre clip di prova indipendenti: **Big Buck Bunny - prova 10 secondi** (test-videos.co.uk), **Video alternativo - W3Schools** e **Flower - prova video MDN**. La versione precedente usava un vecchio file Google/GTV che può restituire HTTP 403 e causare l'errore ExoPlayer 2004. Apri uno dei video dalla scheda e premi **Riproduci film**; se un host risponde con un errore, prova un secondo video. Sono tutti test del player nativo, non un'integrazione di video dai sei cataloghi. I collegamenti sono documentati da fonti pubbliche ma devono essere verificati sul dispositivo finale.

L'esperimento è volutamente separato dai sei siti di catalogo: per questi ultimi **la ricerca rimane solo informativa** e la scritta «Prossimamente» persiste; non vengono ricavati link video né aggirati blocchi o DRM.

## Installazione

CloudStream → **Impostazioni → Estensioni → Aggiungi repository**.

Incolla:

```text
https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/main/repo.json
```

Installa la voce **MultiSource** (beta). La versione 5 rende le fonti selezionabili nel menu principale anche se non esiste ancora un catalogo iniziale; dopo aver scelto la sorgente usa **Cerca** per cercare titoli. La pagina iniziale resta volutamente vuota. Se era già installata, verifica di aver aggiornato a **versione 5** dopo il completamento della [build GitHub Actions](https://github.com/Frenk0371/frenky-cloudstream/actions/workflows/build.yml).

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
