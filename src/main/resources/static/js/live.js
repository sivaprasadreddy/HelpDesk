/*
 * Live updates: refreshes the parts of a page marked [data-live-region] when a ticket shown there changes.
 * The server pushes only {ticketId, kind} over /live/tickets (server-sent events) to users who may view that
 * ticket; the page then fetches itself again and swaps in the new regions. A region that has the focus inside a
 * form waits until the focus leaves it, so nothing the user is typing or choosing is lost.
 */
(() => {
    if (!('EventSource' in window) || !document.querySelector('[data-live-region]')) {
        return;
    }
    const watchedTicket = document.querySelector('[data-live-ticket]')?.dataset.liveTicket;
    const announcer = document.getElementById('live-announcer');
    let timer = null;
    let pending = false;

    const busy = () => {
        const active = document.activeElement;
        return active?.closest('[data-live-region] form') != null;
    };

    const refresh = async () => {
        timer = null;
        if (document.hidden || busy()) {
            pending = true;
            return;
        }
        pending = false;
        let response;
        try {
            response = await fetch(window.location.href, {headers: {Accept: 'text/html'}, credentials: 'same-origin'});
        } catch {
            pending = true;
            return;
        }
        if (response.redirected && new URL(response.url).pathname.endsWith('/login')) {
            source.close();
            return;
        }
        if (!response.ok) {
            window.location.reload();
            return;
        }
        const doc = new DOMParser().parseFromString(await response.text(), 'text/html');
        const regions = [...document.querySelectorAll('[data-live-region]')];
        const replacements = regions.map(region => doc.getElementById(region.id));
        if (replacements.some(r => r == null) || doc.querySelectorAll('[data-live-region]').length !== regions.length) {
            window.location.reload();
            return;
        }
        regions.forEach((region, i) => region.replaceWith(document.adoptNode(replacements[i])));
        if (announcer) {
            announcer.textContent = '';
            announcer.textContent = watchedTicket ? 'This ticket was updated.' : 'The ticket list was updated.';
        }
    };

    const schedule = () => {
        if (timer == null) {
            timer = setTimeout(refresh, 400);
        }
    };

    const source = new EventSource(document.documentElement.dataset.liveUrl || '/live/tickets');
    source.addEventListener('ticket', event => {
        let change;
        try {
            change = JSON.parse(event.data);
        } catch {
            return;
        }
        if (watchedTicket == null || String(change.ticketId) === watchedTicket) {
            schedule();
        }
    });

    document.addEventListener('visibilitychange', () => {
        if (!document.hidden && pending) {
            schedule();
        }
    });
    document.addEventListener('focusout', () => {
        if (pending) {
            setTimeout(() => pending && !busy() && schedule(), 0);
        }
    });
    window.addEventListener('pagehide', () => source.close());
})();
