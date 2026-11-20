import { useCallback, useEffect, useState } from 'react';
import type { CatalogueRequest } from '../api/catalogue';
import {
    getUnreadCount,
    listNotifications,
    markNotificationRead,
    type NotificationDto,
} from '../api/stock';

const POLL_MS = 30_000;

export default function NotificationBell({ request }: { request: CatalogueRequest }) {
    const [count, setCount] = useState(0);
    const [open, setOpen] = useState(false);
    const [items, setItems] = useState<NotificationDto[]>([]);
    const [error, setError] = useState<string | null>(null);

    const refreshCount = useCallback(async () => {
        try {
            setCount(await getUnreadCount(request));
        } catch {
            /* échec silencieux : le prochain polling réessaiera */
        }
    }, [request]);

    const loadItems = useCallback(async () => {
        try {
            setItems(await listNotifications(request, false));
            setError(null);
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Notifications indisponibles.');
        }
    }, [request]);

    // Polling du compteur, en pause quand l'onglet est caché
    useEffect(() => {
        void refreshCount();
        const timer = window.setInterval(() => {
            if (!document.hidden) void refreshCount();
        }, POLL_MS);
        const onChange = () => { void refreshCount(); if (open) void loadItems(); };
        window.addEventListener('stock:changed', onChange);
        return () => {
            window.clearInterval(timer);
            window.removeEventListener('stock:changed', onChange);
        };
    }, [refreshCount, loadItems, open]);

    const toggle = () => {
        const next = !open;
        setOpen(next);
        if (next) void loadItems();
    };

    const read = async (n: NotificationDto) => {
        if (n.read) return;
        try {
            await markNotificationRead(request, n.id);
            setItems((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
            await refreshCount(); // le compteur vient du serveur, pas d'un décrément local
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Impossible de marquer comme lue.');
        }
    };

    const readAll = async () => {
        const unread = items.filter((n) => !n.read);
        try {
            await Promise.all(unread.map((n) => markNotificationRead(request, n.id)));
            setItems((list) => list.map((x) => ({ ...x, read: true })));
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Échec.');
        }
        await refreshCount();
    };

    return (
        <div style={{ position: 'relative' }}>
            <button type="button" onClick={toggle} aria-label={`Notifications, ${count} non lues`}>
                🔔{count > 0 && <strong style={{ color: '#c62828' }}> {count}</strong>}
            </button>
            {open && (
                <div role="dialog" style={{ position: 'absolute', right: 0, zIndex: 10, width: 320, maxHeight: 400, overflow: 'auto', background: '#fff', color: '#111', border: '1px solid #ccc', padding: 8 }}>
                    {error && <p className="alert error" role="alert">{error}</p>}
                    {items.some((n) => !n.read) && (
                        <button type="button" onClick={readAll}>Tout marquer comme lu</button>
                    )}
                    {items.length === 0 && <p>Aucune notification.</p>}
                    <ul style={{ listStyle: 'none', padding: 0 }}>
                        {items.map((n) => (
                            <li key={n.id}>
                                <button
                                    type="button"
                                    onClick={() => read(n)}
                                    style={{ fontWeight: n.read ? 'normal' : 'bold', textAlign: 'left', width: '100%' }}
                                >
                                    {n.message}
                                    <br />
                                    <small>{new Date(n.createdAt).toLocaleString('fr-FR')}</small>
                                </button>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
}