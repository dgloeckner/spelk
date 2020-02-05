#!/usr/bin/env sh

# Wait for Kibana to come up
echo "Waiting for Kibana to boot"
timeout 300 bash -c 'while [[ "$(curl --insecure -s -o /dev/null -w ''%{http_code}'' http://localhost:5601/status)" != "200" ]]; do sleep 5; done'

if ` curl -s -I http://localhost:5601/status | grep -q "200 OK"` ; then
  echo "Kibana is up. Will install dashboards now..."
  cd /tmp/dashboards/
  dashboards=""*.yaml""
  for file in $dashboards
  do
    echo "Will upload $file"
    curl -XPOST localhost:5601/api/kibana/dashboards/import -H 'kbn-xsrf:true' -H 'Content-type:application/json' -d @$file
  done

else
  echo "Cannot install dashboards! Kibana is not up!"
fi