"""Create missing learning databases on an existing Compose volume; never drops data."""
import subprocess
names=['order-srv-db','payment-srv-db','keycloak','product-aggregator-service-db','customer-service-db','product-discount-service-db','rating-service-db','inventory-service-db','notification-service-db']
for name in names:
    exists=subprocess.check_output(['docker','exec','postgres','psql','-U','postgres','-tAc',f"SELECT 1 FROM pg_database WHERE datname='{name}'"],text=True).strip()
    if not exists:
        subprocess.run(['docker','exec','postgres','createdb','-U','postgres',name],check=True)
    print(name+': ready')

from pathlib import Path
migration=Path(__file__).with_name('migrate-commerce.sql').read_text()
subprocess.run(['docker','exec','-i','postgres','psql','-v','ON_ERROR_STOP=1','-U','postgres','-d','order-srv-db'],input=migration,text=True,check=True)
