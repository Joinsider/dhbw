const express = require('express');
const path = require('path');
const app = express();
const port = 3000;

app.use(express.urlencoded({ extended: true }));

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'views', 'login.html'));
});

app.post('/scripts/mgrqispi.dll', (req, res) => {
  const { PRGNAME } = req.body;
  if (PRGNAME === 'LOGINCHECK') {
    res.header('refresh', '0;URL=/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE&ARGUMENTS=-N123456789012345');
    res.send();
  } else {
    res.status(404).send('Not found');
  }
});

app.get('/scripts/mgrqispi.dll', (req, res) => {
  const { PRGNAME } = req.query;
  if (PRGNAME === 'STARTPAGE') {
    res.send('<div id="sessionId"></div><script>window.location.href = "/main";</script>');
  } else if (PRGNAME === 'COURSERESULTS') {
    res.sendFile(path.join(__dirname, 'views', 'grades.html'));
  } else if (PRGNAME === 'SCHEDULE') {
    res.sendFile(path.join(__dirname, 'views', 'timetable.html'));
  } else {
    res.status(404).send('Not found');
  }
});

app.get('/main', (req, res) => {
  res.send(`
    <a href="/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N123456789012345,-N000307,">Prüfungsergebnisse</a>
    <a href="/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=SCHEDULE&ARGUMENTS=-N123456789012345,-A,diese+Woche">Stundenplan diese Woche</a>
    <a href="#">Abmelden</a>
  `);
});

app.listen(port, () => {
  console.log(`Mock server listening at http://localhost:${port}`);
});
