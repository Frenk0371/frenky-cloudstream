# Frenky CloudStream — Beta 6

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

**Importante:** la ricerca è stata verificata con sonde HTTP pubbliche. Non è stata ancora collaudata manualmente su Fire TV o tablet Android. La versione Beta 6 **non implementa l'estrazione né la riproduzione dei film e delle puntate dai cataloghi di terzi**, e non garantisce che ogni ricerca trovi risultati. Nessuna autorizzazione alla distribuzione dei contenuti dei siti è stata verificata.

## Collaudo del lettore integrato (Beta 5)

La voce **Frenky - Test Player** contiene ora tre clip di prova indipendenti: **Big Buck Bunny - prova 10 secondi** (test-videos.co.uk), **Video alternativo - W3Schools** e **Flower - prova video MDN**. La versione precedente usava un vecchio file Google/GTV che può restituire HTTP 403 e causare l'errore ExoPlayer 2004. Apri uno dei video dalla scheda e premi **Riproduci film**; se un host risponde con un errore, prova un secondo video. Sono tutti test del player nativo, non un'integrazione di video dai sei cataloghi. I collegamenti sono documentati da fonti pubbliche ma devono essere verificati sul dispositivo finale.

L'esperimento è volutamente separato dai sei siti di catalogo: per questi ultimi **la ricerca rimane solo informativa** e la scritta «Prossimamente» persiste; non vengono ricavati link video né aggirati blocchi o DRM.

## Film riproducibili: Frenky Cinema Libero (Beta 6)

Sorgente aggiunta nella Beta 6, separata dalle sei sorgenti di metadata.
Contiene **12 cortometraggi interi** del progetto **Blender Open Movies** pubblicati sul server ufficiale **video.blender.org**, tra cui **Big Buck Bunny, Sintel, Tears of Steel, Spring, Elephants Dream, Agent 327, Coffee Run e Sprite Fright**. [Catalogo Blender Studio](https://studio.blender.org/films/).

- Seleziona **Frenky Cinema Libero** dal menu delle sorgenti. I 12 film appaiono nella schermata iniziale.
- Apri un film, premi **Riproduci film**: CloudStream usa direttamente l'MP4 ufficiale a 720p. La ricerca funziona per titolo, parola, anno e genere descrittivo.
- I singoli collegamenti provengono dall'indice pubblico dei flussi del server Blender, verificabile su [FreetuxTV](https://database.freetuxtv.net/WebStream/index?WebStreamSearchForm%5BLanguage%5D=en&WebStreamSearchForm%5BStatus%5D=2&WebStreamSearchForm%5BType%5D=4). Anche un link elencato come attivo può in seguito diventare non disponibile o non essere raggiungibile dal tablet.
- I film sono disponibili in lingua originale, **non sono garantiti audio o sottotitoli italiani**. Ogni film rimanda alla sua pagina sul server originale; la licenza specifica va rispettata e i titoli di coda non devono essere rimossi.
- Il **Test Player** della v5 resta incluso. **Eurostream, CB01, GuardaSerie, Il Genio e gli altri siti restano di sola ricerca**: questa versione non ricava link video da tali siti e non aggira blocchi o DRM.

## Installazione

CloudStream → **Impostazioni → Estensioni → Aggiungi repository**.

Incolla:

```text
https://raw.githubusercontent.com/Frenk0371/frenky-cloudstream/main/repo.json
```

Installa la voce **MultiSource** (beta). La versione 6 rende le fonti selezionabili nel menu principale anche se non esiste ancora un catalogo iniziale; dopo aver scelto la sorgente usa **Cerca** per cercare titoli. La pagina iniziale resta volutamente vuota. Se era già installata, verifica di aver aggiornato a **versione 6** dopo il completamento della [build GitHub Actions](https://github.com/Frenk0371/frenky-cloudstream/actions/workflows/build.yml).

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
