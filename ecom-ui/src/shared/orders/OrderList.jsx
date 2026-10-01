import { useState } from 'react';
import OrdersTable from './OrdersTable';
import { newest, statuses, label } from '../../orders';
export default function OrderList({ orders, ordersPath, showCustomer = false, customer = '' }) {
  const [query, setQuery] = useState(''),
    [status, setStatus] = useState(''),
    [page, setPage] = useState(1);
  const filtered = newest(orders).filter(
    (o) =>
      (!customer || o.customerId === customer) &&
      (!status || o.status === status) &&
      `${o.id} ${o.customerId}`.toLowerCase().includes(query.toLowerCase())
  );
  const pages = Math.max(1, Math.ceil(filtered.length / 10)),
    current = Math.min(page, pages);
  return (
    <>
      <section className="panel">
        {customer && (
          <div className="customer-filter">
            Customer: <strong>{customer}</strong>{' '}
            <a href={`#${ordersPath}`}>Clear customer filter</a>
          </div>
        )}
        <div className="filters">
          <label>
            Search orders
            <input
              type="search"
              placeholder={showCustomer ? 'Order ID or customer username' : 'Order ID'}
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setPage(1);
              }}
            />
          </label>
          <div>
            <label htmlFor="status-filter">Status</label>
            <select
              id="status-filter"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(1);
              }}
            >
              <option value="">All statuses</option>
              {statuses.map((s) => (
                <option key={s} value={s}>
                  {label(s)}
                </option>
              ))}
            </select>
          </div>
          <span className="muted">{filtered.length} results</span>
        </div>
        {filtered.length ? (
          <OrdersTable
            orders={filtered.slice((current - 1) * 10, current * 10)}
            showCustomer={showCustomer}
            ordersPath={ordersPath}
          />
        ) : (
          <div className="empty">
            <h2>{orders.length ? 'No matching orders' : 'Your first order starts here'}</h2>
            <p>
              {orders.length ? 'Try another search or status.' : 'Create an order to get started.'}
            </p>
            {!orders.length && (
              <a href={`#${ordersPath}/new`} className="button">
                Create order
              </a>
            )}
          </div>
        )}
        {filtered.length > 0 && (
          <div className="pagination">
            <span>
              Page {current} of {pages}
            </span>
            <div>
              <button
                className="secondary"
                disabled={current === 1}
                onClick={() => setPage(current - 1)}
              >
                Previous
              </button>
              <button
                className="secondary"
                disabled={current === pages}
                onClick={() => setPage(current + 1)}
              >
                Next
              </button>
            </div>
          </div>
        )}
      </section>
    </>
  );
}
