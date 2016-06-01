//
//  Angara.Net/meteo realtime feed bot
//


// www@crontab:
//  */1 *  *  *  *  /www/angara/meteo/meteo38rt.js


tlg_chat  = "@meteo38rt"
tlg_token = "129123506:AAEq5PEcUNdcC-EHwBYlr--mNSYjH04A6mk"
tlg_api   = "https://api.telegram.org/bot"+tlg_token+"/"
mdb_url   = "mongodb://localhost/meteo"

// make inteval to receive data from remotes
start_delay = 20

tlg_timeout = 8000

request = require('request')
moment  = require('moment')
mongo   = require('mongodb').MongoClient

st_map = {}

data_fields = ['t', 'h', 'p', 'q', 'w', 'g', 'b', 'wt', 'wl']

find_fields = {_id:0, st:1, ext:1}
for(var f in data_fields) {
  find_fields[data_fields[f]] = 1;
}


function send_message(chat, text) {
  request.post({
      url: tlg_api+"sendMessage",
      timeout: tlg_timeout,
      form: {chat_id: chat, parse_mode: "Markdown", text: text}
    },
    function(err, resp, body) {
      if(err) { console.log(err); }
    }
  );

}

function format_data(t, data) {
  if(!data.length) { return; }
  res = [];
  for(var i=0; i < data.length; i++){
    var d = data[i];
    var par = [];
    for(var j=0; j < data_fields.length; j++) {
      var f = data_fields[j];
      if(d[f]) {
        par.push("`"+f+":`"+d[f].toFixed(1));
      }
    }
    var cycle = "", ext = null;
    if( ext = d.ext ) {
        if(ext.cycle) { cycle = ":"+ext.cycle; }
    };
    res.push("\n*"+(st_map[d.st] || d.st)+"*  `/"+d.st+cycle+"/`\n "+par.join(" "));
  }
  return t.format("= MMM D, HH:mm =")+"\n"+res.join("\n");
}

mongo.connect(mdb_url, function(err, db) {
  if(err) {
    return console.error(err);
  }

  setTimeout( function() {
    var t1 = moment().seconds(start_delay).milliseconds(0);
    var t0 = t1.clone().subtract(60, "seconds");

    db.collection('dat').find(
      {
        ts:{$gte:t0.toDate(), $lt:t1.toDate()},
        icao:{$exists:false}, gcn:{$exists:false}
      },
      find_fields
    ).limit(1000).toArray(function(err, res){
      db.close();
      if(err) { return console.error(err); }
      var t = format_data(t1.seconds(0), res);
      if(t) {
        send_message(tlg_chat, t);
      }
    });
  }, start_delay*1000);

  db.collection('st').find({},{_id:1,title:1}).toArray(
    function(err,data){
      if(err) { return console.error(err); }
      data.forEach(function(st){ st_map[st._id] = st.title; })
    }
  );
})

//.
