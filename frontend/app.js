/* Courier Parcel Tracking - application shell.
   Vanilla JS, no build step, no dependencies.
   All data comes from the backend API; nothing here is mocked. */

(function () {
  "use strict";

  /* ------------------------------------------------------------------ */
  /* Constants                                                           */
  /* ------------------------------------------------------------------ */

  var PAGE_SIZE = 10;

  var STATUS_LABEL = {
    BOOKED: "Booked",
    PICKED_UP: "Picked up",
    IN_TRANSIT: "In transit",
    OUT_FOR_DELIVERY: "Out for delivery",
    DELIVERED: "Delivered",
    DELIVERY_FAILED: "Delivery failed",
    EXCEPTION: "Exception"
  };

  var STATUS_ORDER = ["BOOKED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"];

  var PIPELINE_DESC = {
    BOOKED: "Booking accepted",
    PICKED_UP: "Collected from sender",
    IN_TRANSIT: "Between sorting hubs",
    OUT_FOR_DELIVERY: "With the delivery courier",
    DELIVERED: "Handed to the recipient"
  };

  var PAYMENT_STATUS_LABEL = {
    PENDING: "Pending",
    SUCCESS: "Paid",
    FAILED: "Failed",
    CASH_ON_DELIVERY: "Cash on delivery"
  };

  var ROLE_LABEL = { CUSTOMER: "Customer", COURIER: "Courier", ADMIN: "Administrator" };

  var COURIER_STATUSES = ["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERY_FAILED", "DELIVERED", "EXCEPTION"];

  /* ------------------------------------------------------------------ */
  /* State                                                               */
  /* ------------------------------------------------------------------ */

  var session = loadSession();
  var profile = null;
  var navOpen = false;
  var pageTitle = "Courier Parcel Tracking";

  /* ------------------------------------------------------------------ */
  /* Small utilities                                                     */
  /* ------------------------------------------------------------------ */

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function fmtDate(value) {
    if (!value) return "Not set";
    var d = new Date(value);
    if (isNaN(d.getTime())) return "Not set";
    return d.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
  }

  function fmtMoney(amount, currency) {
    var n = Number(amount);
    if (isNaN(n)) return "-";
    return (currency || "INR") + " " + n.toFixed(2);
  }

  function icon(name, cls) {
    return '<svg class="icon ' + (cls || "") + '" aria-hidden="true"><use href="#i-' + name + '"/></svg>';
  }

  function statusBadge(status) {
    var s = String(status || "BOOKED");
    return '<span class="badge badge--' + esc(s) + '">' + esc(STATUS_LABEL[s] || s.replace(/_/g, " ")) + "</span>";
  }

  function paymentBadge(status) {
    var s = String(status || "PENDING");
    return '<span class="badge badge--' + esc(s) + '">' + esc(PAYMENT_STATUS_LABEL[s] || s.replace(/_/g, " ")) + "</span>";
  }

  function roleBadge(role) {
    var r = String(role || "CUSTOMER");
    return '<span class="badge badge--' + esc(r) + '">' + esc(ROLE_LABEL[r] || r) + "</span>";
  }

  function tid(value, big) {
    return '<span class="tid' + (big ? " tid--lg" : "") + '">' + esc(value) + "</span>";
  }

  function emptyState(title, body, iconName) {
    return '<div class="empty">' + icon(iconName || "inbox", "empty__icon") +
      '<div class="empty__title">' + esc(title) + "</div>" +
      (body ? '<p class="empty__body">' + esc(body) + "</p>" : "") + "</div>";
  }

  function skeletonList(n) {
    var out = '<div class="ship-list" aria-hidden="true">';
    for (var i = 0; i < (n || 3); i++) out += '<div class="skeleton skeleton--block"></div>';
    return out + "</div>";
  }

  function skeletonRows(n) {
    var out = '<div aria-hidden="true" style="padding:16px">';
    for (var i = 0; i < (n || 5); i++) out += '<div class="skeleton skeleton--row"></div>';
    return out + "</div>";
  }

  /* ------------------------------------------------------------------ */
  /* Session persistence                                                 */
  /* ------------------------------------------------------------------ */

  function loadSession() {
    try {
      var raw = window.sessionStorage.getItem("cpt.session");
      return raw ? JSON.parse(raw) : null;
    } catch (e) { return null; }
  }

  function saveSession(value) {
    session = value;
    try {
      if (value) window.sessionStorage.setItem("cpt.session", JSON.stringify(value));
      else window.sessionStorage.removeItem("cpt.session");
    } catch (e) { /* storage unavailable: session stays in memory only */ }
  }

  /* ------------------------------------------------------------------ */
  /* API                                                                 */
  /* ------------------------------------------------------------------ */

  function api(path, options) {
    options = options || {};
    var headers = { "Content-Type": "application/json" };
    if (options.headers) {
      Object.keys(options.headers).forEach(function (k) { headers[k] = options.headers[k]; });
    }
    if (session) {
      headers.Authorization = "Basic " + window.btoa(session.email + ":" + session.password);
    }
    return window.fetch("/api" + path, { method: options.method || "GET", headers: headers, body: options.body })
      .then(function (res) {
        return res.text().then(function (text) {
          if (!res.ok) {
            var message = res.status + " " + res.statusText;
            try {
              var parsed = JSON.parse(text);
              if (parsed && parsed.message) message = parsed.message;
              else if (parsed && parsed.detail) message = parsed.detail;
            } catch (e) { /* keep status text */ }
            throw new Error(message);
          }
          return text ? JSON.parse(text) : null;
        });
      });
  }

  /* ------------------------------------------------------------------ */
  /* Toasts                                                              */
  /* ------------------------------------------------------------------ */

  function toast(message, kind) {
    var region = $("#toastRegion");
    var el = document.createElement("div");
    var tone = kind || (/fail|error|could not|unavailable|denied/i.test(message) ? "error" : "success");
    el.className = "toast toast--" + tone;
    el.innerHTML = icon(tone === "error" ? "alert-circle" : "check-circle") + "<span>" + esc(message) + "</span>";
    region.appendChild(el);
    window.setTimeout(function () {
      if (el.parentNode) el.parentNode.removeChild(el);
    }, 5200);
  }

  /* ------------------------------------------------------------------ */
  /* Busy wrapper                                                        */
  /* ------------------------------------------------------------------ */

  function busy(btn, fn) {
    if (!btn) return fn();
    var original = btn.innerHTML;
    btn.disabled = true;
    btn.setAttribute("aria-busy", "true");
    btn.innerHTML = icon("loader") + "<span>Working</span>";
    return Promise.resolve().then(fn).then(function (r) {
      btn.disabled = false;
      btn.removeAttribute("aria-busy");
      btn.innerHTML = original;
      return r;
    }, function (err) {
      btn.disabled = false;
      btn.removeAttribute("aria-busy");
      btn.innerHTML = original;
      throw err;
    });
  }

  /* ------------------------------------------------------------------ */
  /* Navigation                                                          */
  /* ------------------------------------------------------------------ */

  /* In-page anchors such as #main must not re-route the app. */
  function currentHash() {
    var h = window.location.hash || "#/";
    if (h.charAt(1) !== "/") return null;
    return h.slice(1) || "/";
  }

  function go(path) {
    if (window.location.hash === "#" + path) render();
    else window.location.hash = path;
  }

  function navItems() {
    var role = profile && profile.role;
    var items = [{ href: "/", label: "Track", icon: "search" }];
    if (!role) {
      items.push({ href: "/signin", label: "Sign in", icon: "user" });
      items.push({ href: "/register", label: "Create account", icon: "plus" });
      return items;
    }
    if (role === "CUSTOMER") {
      items = [
        { href: "/shipments", label: "My shipments", icon: "package" },
        { href: "/book", label: "New booking", icon: "plus" },
        { href: "/notifications", label: "Notifications", icon: "bell" },
        { href: "/support", label: "Support", icon: "message" }
      ];
    } else if (role === "COURIER") {
      items = [
        { href: "/deliveries", label: "My deliveries", icon: "truck" },
        { href: "/account", label: "Account", icon: "user" }
      ];
    } else if (role === "ADMIN") {
      items = [
        { href: "/admin", label: "Overview", icon: "dashboard" },
        { href: "/admin/parcels", label: "Parcels", icon: "package" },
        { href: "/admin/assignments", label: "Assignments", icon: "route" },
        { href: "/admin/users", label: "Users", icon: "users" },
        { href: "/admin/payments", label: "Payments", icon: "credit-card" },
        { href: "/admin/activity", label: "Activity", icon: "activity" },
        { href: "/admin/events", label: "Failed events", icon: "alert-triangle" },
        { href: "/admin/emails", label: "Emails", icon: "mail" }
      ];
    }
    return items;
  }

  function renderNav() {
    var path = currentHash() || "/";
    var items = navItems();
    var html = items.map(function (it) {
      var active = path === it.href || (it.href !== "/" && path.indexOf(it.href + "/") === 0);
      return '<a href="#' + it.href + '"' + (active ? ' aria-current="page"' : "") + ">" + esc(it.label) + "</a>";
    }).join("");

    var nav = $("#primaryNav");
    nav.innerHTML = html;
    nav.classList.toggle("is-open", navOpen);
    $("#navToggle").setAttribute("aria-expanded", String(navOpen));
    $("#navToggle").setAttribute("aria-label", navOpen ? "Close menu" : "Open menu");
    $("#navToggle").innerHTML = icon(navOpen ? "x" : "menu");

    var area = $("#accountArea");
    if (profile) {
      var initial = (profile.name || "?").charAt(0).toUpperCase();
      area.innerHTML =
        '<div class="row" style="gap:12px">' +
          '<a class="user-chip" href="#/account" aria-label="Account settings for ' + esc(profile.name) + '">' +
            '<span class="user-chip__avatar" aria-hidden="true">' + esc(initial) + "</span>" +
            '<span class="user-chip__meta">' +
              '<span class="user-chip__name">' + esc(profile.name) + "</span>" +
              '<span class="user-chip__role">' + esc(ROLE_LABEL[profile.role] || profile.role) + "</span>" +
            "</span>" +
          "</a>" +
          '<button class="btn btn--secondary btn--sm" type="button" id="signOut">' + icon("log-out", "icon-sm") + "Sign out</button>" +
        "</div>";
      $("#signOut").addEventListener("click", function () {
        saveSession(null);
        profile = null;
        toast("Signed out.");
        go("/");
      });
    } else {
      area.innerHTML = '<a class="btn btn--primary btn--sm" href="#/signin">Sign in</a>';
    }

    var footerLinks = $("#footerAccountLinks");
    if (profile) {
      footerLinks.innerHTML =
        '<li><a href="#/account">Account settings</a></li>' +
        (profile.role === "CUSTOMER" ? '<li><a href="#/support">Contact support</a></li>' : "") +
        (profile.role === "ADMIN" ? '<li><a href="#/admin">Administration</a></li>' : "");
    }
  }

  /* ------------------------------------------------------------------ */
  /* Shared fragments                                                    */
  /* ------------------------------------------------------------------ */

  function routeStrip(p) {
    return '<div class="route-strip">' +
      '<div class="route-strip__end"><div class="route-strip__label">From</div>' +
        '<div class="route-strip__value">' + esc(p.senderAddress) + "</div>" +
        '<div class="muted tiny">' + esc(p.senderName) + "</div></div>" +
      '<div class="route-strip__arrow" aria-hidden="true">' + icon("arrow-right") + "</div>" +
      '<div class="route-strip__end route-strip__end--to"><div class="route-strip__label">To</div>' +
        '<div class="route-strip__value">' + esc(p.receiverAddress) + "</div>" +
        '<div class="muted tiny">' + esc(p.receiverName) + "</div></div>" +
    "</div>";
  }

  function detailGrid(rows) {
    return '<dl class="detail-grid">' + rows.map(function (r) {
      return "<div><dt>" + esc(r[0]) + "</dt><dd>" + (r[2] ? r[1] : esc(r[1])) + "</dd></div>";
    }).join("") + "</dl>";
  }

  function timeline(history) {
    if (!history || !history.length) return emptyState("No tracking events yet", "Events appear here as soon as the parcel moves.", "clock");
    var last = history.length - 1;
    return '<ol class="timeline">' + history.map(function (h, i) {
      var cls = i === last ? "timeline__item timeline__item--current" : "timeline__item timeline__item--done";
      return '<li class="' + cls + '"' + (i === last ? ' aria-current="step"' : "") + ">" +
        '<div class="timeline__head">' + statusBadge(h.status) + "</div>" +
        '<div class="timeline__note">' + esc(h.note || "Status updated") + "</div>" +
        '<div class="timeline__time">' + esc(fmtDate(h.occurredAt)) + "</div>" +
      "</li>";
    }).join("") + "</ol>";
  }

  function pipeline(currentStatus) {
    var idx = STATUS_ORDER.indexOf(currentStatus);
    if (idx < 0) idx = 0;
    return '<div class="pipeline">' + STATUS_ORDER.map(function (s, i) {
      var cls = i < idx ? "pipeline__step pipeline__step--done" :
                (i === idx ? "pipeline__step pipeline__step--current" : "pipeline__step");
      return '<div class="' + cls + '">' +
        '<div class="pipeline__n">' + (i + 1) + "</div>" +
        '<div class="pipeline__label">' + esc(STATUS_LABEL[s]) + "</div>" +
        '<div class="pipeline__desc">' + esc(PIPELINE_DESC[s]) + "</div>" +
      "</div>";
    }).join("") + "</div>";
  }

  function shipCard(p, href) {
    return '<a class="ship-card" href="' + esc(href) + '">' +
      '<div class="ship-card__top">' + tid(p.trackingId) + statusBadge(p.status) + "</div>" +
      '<div class="ship-card__route">' +
        '<div class="ship-card__from"><div class="ship-card__place">' + esc(p.senderAddress) + "</div>" +
          '<div class="ship-card__who">' + esc(p.senderName) + "</div></div>" +
        icon("arrow-right") +
        '<div class="ship-card__to"><div class="ship-card__place">' + esc(p.receiverAddress) + "</div>" +
          '<div class="ship-card__who">' + esc(p.receiverName) + "</div></div>" +
      "</div>" +
      '<div class="ship-card__meta">' +
        "<span>" + icon("package", "icon-sm") + esc(p.description || "Parcel") + "</span>" +
        "<span>" + icon("calendar", "icon-sm") + "ETA " + esc(fmtDate(p.expectedDelivery)) + "</span>" +
        "<span>" + icon("credit-card", "icon-sm") + esc(fmtMoney(p.shippingCost, "INR")) + "</span>" +
      "</div>" +
    "</a>";
  }

  function noteItem(n, isNew) {
    return '<li class="note">' +
      '<span class="note__icon' + (isNew ? " note__icon--new" : "") + '" aria-hidden="true">' + icon(isNew ? "bell" : "inbox", "icon-sm") + "</span>" +
      '<div class="note__body"><div class="note__text">' + esc(n.message) + "</div>" +
      '<div class="note__meta"><span>' + esc(n.trackingId || "") + "</span><span>" + esc(fmtDate(n.createdAt)) + "</span></div></div>" +
    "</li>";
  }

  /* ------------------------------------------------------------------ */
  /* Tables with client-side search, filter and paging                   */
  /* ------------------------------------------------------------------ */

  function dataTable(opts) {
    var id = opts.id;
    var rows = opts.rows || [];
    var cols = opts.cols || [];
    var searchKeys = opts.searchKeys || [];
    var filters = opts.filters || [];
    var page = Number(opts.page || 1);

    var search = (opts.searchState && opts.searchState.q) || "";
    var filterValue = (opts.searchState && opts.searchState.f) || "";

    var filtered = rows;
    if (search) {
      var needle = search.toLowerCase();
      filtered = filtered.filter(function (r) {
        return searchKeys.some(function (k) {
          var v = typeof k === "function" ? k(r) : r[k];
          return v != null && String(v).toLowerCase().indexOf(needle) !== -1;
        });
      });
    }
    if (filterValue && opts.filterKey) {
      filtered = filtered.filter(function (r) { return String(r[opts.filterKey]) === filterValue; });
    }

    var total = filtered.length;
    var pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
    if (page > pages) page = pages;
    var start = (page - 1) * PAGE_SIZE;
    var slice = filtered.slice(start, start + PAGE_SIZE);

    var toolbar = '<div class="table-toolbar" role="search">' +
      '<div class="table-toolbar__search">' + icon("search", "icon-sm") +
        '<label class="visually-hidden" for="' + id + '-search">' + esc(opts.label || "Search") + "</label>" +
        '<input id="' + id + '-search" type="search" data-table="' + id + '" data-role="search" placeholder="' + esc(opts.searchPlaceholder || "Search") + '" value="' + esc(search) + '" />' +
      "</div>" +
      filters.map(function (f) {
        return '<label class="visually-hidden" for="' + id + '-f-' + esc(f.key) + '">' + esc(f.label) + "</label>" +
          '<select id="' + id + '-f-' + esc(f.key) + '" data-table="' + id + '" data-role="filter">' +
            '<option value="">' + esc(f.allLabel) + "</option>" +
            f.options.map(function (o) {
              return '<option value="' + esc(o.value) + '"' + (filterValue === o.value ? " selected" : "") + ">" + esc(o.label) + "</option>";
            }).join("") +
          "</select>";
      }).join("") +
      '<span class="table-toolbar__count">' + total + " of " + rows.length + " shown</span>" +
    "</div>";

    if (!total) {
      return toolbar + '<div style="padding:16px">' + emptyState(
        rows.length ? "No matching records" : (opts.emptyTitle || "Nothing to show yet"),
        rows.length ? "Try a different search term or clear the filter." : (opts.emptyBody || "Records will appear here."),
        opts.emptyIcon || "inbox"
      ) + "</div>";
    }

    var head = cols.map(function (c) {
      return '<th' + (c.num ? ' class="num"' : "") + (c.actions ? ' class="actions"' : "") + ">" + esc(c.label) + "</th>";
    }).join("");

    var body = slice.map(function (r) {
      var attrs = opts.rowAttrs ? opts.rowAttrs(r) : "";
      var cls = opts.rowClass ? opts.rowClass(r) : "";
      return "<tr" + (cls ? ' class="' + cls + '"' : "") + attrs + ">" +
        cols.map(function (c) {
          return "<td" + (c.num ? ' class="num"' : "") + (c.actions ? ' class="actions"' : "") + ">" + c.render(r) + "</td>";
        }).join("") + "</tr>";
    }).join("");

    var paging = '<div class="pagination">' +
      '<span class="pagination__info">Showing ' + (start + 1) + " to " + (start + slice.length) + " of " + total + "</span>" +
      '<span class="pagination__controls">' +
        '<button class="btn btn--secondary btn--sm" type="button" data-table="' + id + '" data-role="prev"' + (page <= 1 ? " disabled" : "") + ">" + icon("chevron-left", "icon-sm") + "Previous</button>" +
        '<span class="tiny muted">Page ' + page + " of " + pages + "</span>" +
        '<button class="btn btn--secondary btn--sm" type="button" data-table="' + id + '" data-role="next"' + (page >= pages ? " disabled" : "") + ">Next" + icon("chevron-right", "icon-sm") + "</button>" +
      "</span>" +
    "</div>";

    return toolbar + '<div class="table-wrap"><table class="data"><thead><tr>' + head + "</tr></thead><tbody>" + body + "</tbody></table></div>" + paging;
  }

  /* Table state per view, so paging survives re-renders. */
  var tableState = {};

  function wireTable(root, key, onRefresh) {
    var state = tableState[key] || (tableState[key] = { q: "", f: "", page: 1 });
    $$("[data-role='search']", root).forEach(function (input) {
      if (input.getAttribute("data-table") !== key) return;
      input.addEventListener("input", function () {
        state.q = input.value;
        state.page = 1;
        onRefresh();
      });
    });
    $$("[data-role='filter']", root).forEach(function (sel) {
      if (sel.getAttribute("data-table") !== key) return;
      sel.addEventListener("change", function () {
        state.f = sel.value;
        state.page = 1;
        onRefresh();
      });
    });
    $$("[data-role='prev']", root).forEach(function (b) {
      if (b.getAttribute("data-table") !== key) return;
      b.addEventListener("click", function () { state.page = Math.max(1, state.page - 1); onRefresh(); });
    });
    $$("[data-role='next']", root).forEach(function (b) {
      if (b.getAttribute("data-table") !== key) return;
      b.addEventListener("click", function () { state.page = state.page + 1; onRefresh(); });
    });
  }

  /* ------------------------------------------------------------------ */
  /* Views: public                                                       */
  /* ------------------------------------------------------------------ */

  function viewLanding() {
    document.title = "Track a shipment | Courier Parcel Tracking";
    return '' +
      '<section class="hero"><div class="hero__inner">' +
        '<p class="hero__eyebrow">Shipment tracking</p>' +
        "<h1>Track a parcel</h1>" +
        '<p class="hero__sub">Enter the tracking ID from your booking confirmation to see the current status and full delivery history.</p>' +
        '<div class="track-panel">' +
          '<label class="track-panel__label" for="trackId">Tracking ID</label>' +
          '<form id="trackForm" class="input-group">' +
            '<input id="trackId" name="id" required placeholder="CP000000000000" autocomplete="off" spellcheck="false" />' +
            '<button class="btn btn--primary" type="submit">' + icon("search", "icon-sm") + "Track shipment</button>" +
          "</form>" +
          '<p class="track-panel__hint">Tracking IDs start with CP and are shown on your booking receipt.</p>' +
        "</div>" +
        '<div id="trackResult" class="tracking-result" aria-live="polite"></div>' +
      "</div></section>" +
      '<div class="page">' +
        '<section class="section">' +
          '<div class="section__head"><div><h2>How a shipment moves</h2>' +
          "<p>Every parcel follows these five stages. Your tracking history maps onto them.</p></div></div>" +
          pipeline("BOOKED") +
        "</section>" +
        (profile ? "" :
        '<section class="section"><div class="auth-split">' +
          '<div class="card"><div class="card__head"><h2>' + icon("user") + "Sign in</h2></div>" +
            '<div class="card__body">' + signInForm("signinForm") + "</div></div>" +
          '<div class="auth-aside"><h2>Shipping with us</h2>' +
            "<p>Create an account to book a parcel, follow it in real time and keep every receipt in one place.</p>" +
            "<ul>" +
              "<li>" + icon("package") + "<span>Book a parcel and get an instant shipping quote</span></li>" +
              "<li>" + icon("bell") + "<span>Receive a notification at every status change</span></li>" +
              "<li>" + icon("credit-card") + "<span>Pay online or choose cash on delivery</span></li>" +
            "</ul>" +
            '<p style="margin-top:24px"><a class="btn btn--secondary" href="#/register">Create an account</a></p>' +
          "</div>" +
        "</div></section>") +
      "</div>";
  }

  function signInForm(id, buttonText) {
    return '<form id="' + id + '" class="stack">' +
      '<div class="field"><label for="' + id + '-email">Email</label>' +
        '<input id="' + id + '-email" name="email" type="email" required autocomplete="username" /></div>' +
      '<div class="field"><label for="' + id + '-pw">Password</label>' +
        '<div class="pw-wrap"><input id="' + id + '-pw" name="password" type="password" required autocomplete="current-password" />' +
        '<button class="btn btn--secondary" type="button" data-pw-toggle="' + id + '-pw">Show</button></div></div>' +
      '<button class="btn btn--primary btn--block" type="submit">' + esc(buttonText || "Sign in") + "</button>" +
    "</form>";
  }

  function viewSignIn() {
    document.title = "Sign in | Courier Parcel Tracking";
    return '<div class="page page--narrow">' +
      '<div class="page-head"><p class="page-head__eyebrow">Account</p><h1>Sign in</h1>' +
      '<p class="page-head__sub">Use the email and password you registered with.</p></div>' +
      '<div class="card"><div class="card__body">' + signInForm("signinForm") +
      '<p class="small muted" style="margin-top:20px">No account yet? <a href="#/register">Create one</a>.</p>' +
      "</div></div></div>";
  }

  function viewRegister() {
    document.title = "Create an account | Courier Parcel Tracking";
    return '<div class="page page--narrow">' +
      '<div class="page-head"><p class="page-head__eyebrow">Account</p><h1>Create an account</h1>' +
      '<p class="page-head__sub">Customer accounts can book parcels, track them and manage payments.</p></div>' +
      '<div class="card"><div class="card__body">' +
      '<form id="registerForm" class="stack">' +
        '<div class="field"><label for="reg-name">Full name</label><input id="reg-name" name="name" required autocomplete="name" /></div>' +
        '<div class="field"><label for="reg-email">Email</label><input id="reg-email" name="email" type="email" required autocomplete="email" /></div>' +
        '<div class="field"><label for="reg-pw">Password</label>' +
          '<input id="reg-pw" name="password" type="password" minlength="8" required autocomplete="new-password" />' +
          '<span class="field__hint">At least 8 characters.</span></div>' +
        '<button class="btn btn--primary btn--block" type="submit">Create account</button>' +
      "</form>" +
      '<p class="small muted" style="margin-top:20px">Already registered? <a href="#/signin">Sign in</a>.</p>' +
      "</div></div></div>";
  }

  /* ------------------------------------------------------------------ */
  /* Views: customer                                                     */
  /* ------------------------------------------------------------------ */

  function viewShipments() {
    document.title = "My shipments | Courier Parcel Tracking";
    return '<div class="page">' +
      '<div class="page-head"><p class="page-head__eyebrow">Customer</p><h1>My shipments</h1>' +
      '<p class="page-head__sub">Every parcel you have booked. Select one to see its full history.</p></div>' +
      '<div class="card"><div class="card__head"><h2>' + icon("package") + "Parcels</h2>" +
        '<button class="btn btn--secondary btn--sm" type="button" id="refreshShipments">' + icon("refresh", "icon-sm") + "Refresh</button></div>" +
        '<div class="card__body" id="shipmentsBody">' + skeletonList(3) + "</div></div>" +
    "</div>";
  }

  function loadShipments() {
    var body = $("#shipmentsBody");
    if (!body) return;
    api("/parcels").then(function (parcels) {
      if (!parcels || !parcels.length) {
        body.innerHTML = emptyState("No shipments yet", "Book a parcel and it will appear here straight away.", "package") +
          '<p style="margin-top:16px"><a class="btn btn--primary" href="#/book">Book a parcel</a></p>';
        return;
      }
      var state = tableState.shipments || (tableState.shipments = { q: "", f: "", page: 1 });
      var filtered = parcels.filter(function (p) {
        var matchesQ = !state.q || [p.trackingId, p.receiverName, p.receiverAddress, p.description]
          .some(function (v) { return String(v || "").toLowerCase().indexOf(state.q.toLowerCase()) !== -1; });
        var matchesF = !state.f || p.status === state.f;
        return matchesQ && matchesF;
      });
      body.innerHTML =
        '<div class="table-toolbar" role="search">' +
          '<div class="table-toolbar__search">' + icon("search", "icon-sm") +
            '<label class="visually-hidden" for="ship-search">Search shipments</label>' +
            '<input id="ship-search" type="search" placeholder="Search tracking ID, recipient or address" value="' + esc(state.q) + '" /></div>' +
          '<label class="visually-hidden" for="ship-filter">Filter by status</label>' +
          '<select id="ship-filter"><option value="">All statuses</option>' +
            Object.keys(STATUS_LABEL).map(function (s) {
              return '<option value="' + esc(s) + '"' + (state.f === s ? " selected" : "") + ">" + esc(STATUS_LABEL[s]) + "</option>";
            }).join("") +
          "</select>" +
          '<span class="table-toolbar__count">' + filtered.length + " of " + parcels.length + " shown</span>" +
        "</div>" +
        (filtered.length
          ? '<div class="ship-list" style="margin-top:16px">' + filtered.map(function (p) {
              return shipCard(p, "/shipments/" + encodeURIComponent(p.trackingId));
            }).join("") + "</div>"
          : emptyState("No matching shipments", "Try a different search term or clear the status filter.", "search"));

      $("#ship-search").addEventListener("input", function (e) { state.q = e.target.value; loadShipments(); });
      $("#ship-filter").addEventListener("change", function (e) { state.f = e.target.value; loadShipments(); });
    }).catch(function (err) {
      body.innerHTML = '<div class="alert alert--error">' + icon("alert-circle") + "<span>Could not load shipments: " + esc(err.message) + "</span></div>";
    });
  }

  function viewShipmentDetail(id) {
    document.title = "Shipment " + id + " | Courier Parcel Tracking";
    return '<div class="page">' +
      '<p><a href="#/shipments">' + icon("chevron-left", "icon-sm") + " Back to my shipments</a></p>" +
      '<div class="page-head" style="margin-top:12px"><p class="page-head__eyebrow">Shipment</p>' +
      '<h1>' + tid(id, true) + "</h1></div>" +
      '<div class="card"><div class="card__body" id="shipmentDetail">' + skeletonRows(6) + "</div></div>" +
    "</div>";
  }

  function loadShipmentDetail(id) {
    var box = $("#shipmentDetail");
    if (!box) return;
    Promise.all([
      api("/parcels"),
      api("/parcels/" + encodeURIComponent(id) + "/history"),
      api("/payments/" + encodeURIComponent(id))
    ]).then(function (results) {
      var parcels = results[0] || [];
      var history = results[1] || [];
      var payment = results[2] || null;
      var p = null;
      for (var i = 0; i < parcels.length; i++) {
        if (parcels[i].trackingId === id) { p = parcels[i]; break; }
      }
      if (!p) {
        box.innerHTML = '<div class="alert alert--error">' + icon("alert-circle") +
          "<span>This shipment does not exist or belongs to another account.</span></div>";
        return;
      }
      box.innerHTML =
        '<div class="row row--between" style="margin-bottom:16px">' + statusBadge(p.status) +
          '<span class="small muted">Booked ' + esc(fmtDate(p.createdAt)) + "</span></div>" +
        routeStrip(p) +
        '<div class="section"><div class="section__head"><h2>Progress</h2></div>' + pipeline(p.status) + "</div>" +
        '<div class="section"><div class="section__head"><h2>Details</h2></div>' +
          detailGrid([
            ["Description", p.description || "Not provided"],
            ["Weight", p.weightKg + " kg"],
            ["Route distance", p.distanceKm + " km"],
            ["Delivery speed", (p.deliverySpeed || "").replace(/_/g, " ").toLowerCase()],
            ["Shipping cost", fmtMoney(p.shippingCost, "INR")],
            ["Expected delivery", fmtDate(p.expectedDelivery)],
            ["Sender", p.senderName + " · " + p.senderPhone],
            ["Receiver", p.receiverName + " · " + p.receiverPhone]
          ]) + "</div>" +
        '<div class="section"><div class="section__head"><h2>Payment</h2></div>' +
          (payment
            ? '<div class="row row--between"><div>' + paymentBadge(payment.status) +
                '<div class="small muted" style="margin-top:6px">' + esc(fmtMoney(payment.amount, payment.currency)) +
                (payment.method ? " · " + esc(payment.method.replace(/_/g, " ")) : "") +
                (payment.transactionReference ? " · " + esc(payment.transactionReference) : "") + "</div></div></div>"
            : '<p class="small muted">No payment record for this shipment.</p>') +
        "</div>" +
        '<div class="section"><div class="section__head"><h2>Tracking history</h2></div>' + timeline(history) + "</div>";
    }).catch(function (err) {
      box.innerHTML = '<div class="alert alert--error">' + icon("alert-circle") +
        "<span>Could not load this shipment: " + esc(err.message) + "</span></div>";
    });
  }

  function viewBook() {
    document.title = "New booking | Courier Parcel Tracking";
    return '<div class="page page--wide">' +
      '<div class="page-head"><p class="page-head__eyebrow">Customer</p><h1>Book a parcel</h1>' +
      '<p class="page-head__sub">Enter the sender, receiver and parcel details. The shipping quote updates as you type.</p></div>' +
      '<div class="grid-auto">' +
        '<div class="card"><div class="card__head"><h2>' + icon("clipboard") + "Booking details</h2></div>" +
          '<div class="card__body">' +
          '<form id="bookForm" class="form-grid">' +
            '<div class="form-section">Sender</div>' +
            '<div class="full"><div class="field"><label for="b-sname">Sender name</label><input id="b-sname" name="senderName" required /></div></div>' +
            '<div><div class="field"><label for="b-sphone">Sender phone</label><input id="b-sphone" name="senderPhone" required /></div></div>' +
            '<div><div class="field"><label for="b-saddr">Sender address</label><input id="b-saddr" name="senderAddress" required /></div></div>' +
            '<div class="form-section">Receiver</div>' +
            '<div class="full"><div class="field"><label for="b-rname">Receiver name</label><input id="b-rname" name="receiverName" required /></div></div>' +
            '<div><div class="field"><label for="b-rphone">Receiver phone</label><input id="b-rphone" name="receiverPhone" required /></div></div>' +
            '<div><div class="field"><label for="b-raddr">Receiver address</label><input id="b-raddr" name="receiverAddress" required /></div></div>' +
            '<div class="form-section">Parcel</div>' +
            '<div class="full"><div class="field"><label for="b-desc">Description</label><input id="b-desc" name="description" required /></div></div>' +
            '<div><div class="field"><label for="b-weight">Weight (kg)</label>' +
              '<input id="b-weight" name="weightKg" type="number" min="0.01" step="any" required placeholder="2" /></div></div>' +
            '<div><div class="field"><label for="b-dist">Route distance (km)</label>' +
              '<input id="b-dist" name="distanceKm" type="number" min="1" step="any" required placeholder="50" /></div></div>' +
            '<div class="full"><div class="field"><label for="b-speed">Delivery speed</label>' +
              '<select id="b-speed" name="deliverySpeed">' +
                '<option value="STANDARD">Standard</option>' +
                '<option value="SAME_DAY">Same day</option>' +
              "</select></div></div>" +
            '<div class="full"><div class="field"><label for="b-eta">Expected delivery (optional)</label>' +
              '<input id="b-eta" name="expectedDelivery" type="datetime-local" /></div></div>' +
            '<div class="full"><button class="btn btn--primary btn--block" type="submit">Book parcel</button></div>' +
          "</form></div></div>" +
        '<div class="card"><div class="card__head"><h2>' + icon("credit-card") + "Shipping quote</h2></div>" +
          '<div class="card__body"><div id="quoteBox" aria-live="polite">' +
            emptyState("No quote yet", "Enter weight, distance and both addresses to calculate the shipping cost.", "scale") +
          "</div><div id='bookResult'></div></div></div>" +
      "</div></div>";
  }

  function loadQuote() {
    var form = $("#bookForm");
    var box = $("#quoteBox");
    if (!form || !box) return;
    var d = formData(form);
    if (!d.weightKg || !d.distanceKm || !d.senderAddress || !d.receiverAddress) return;
    api("/parcels/calculate-cost", {
      method: "POST",
      body: JSON.stringify({
        source: d.senderAddress,
        destination: d.receiverAddress,
        weightKg: Number(d.weightKg),
        distanceKm: Number(d.distanceKm),
        deliverySpeed: d.deliverySpeed || "STANDARD"
      })
    }).then(function (q) {
      box.innerHTML = detailGrid([
        ["Base rate", fmtMoney(q.basePrice, "INR")],
        ["Weight charge", fmtMoney(q.weightCharge, "INR")],
        ["Distance charge", fmtMoney(q.distanceCharge, "INR")],
        ["Speed charge", fmtMoney(q.deliverySpeedCharge, "INR")]
      ]) + '<p style="margin-top:20px"><span class="label-eyebrow">Estimated total</span>' +
        '<span style="display:block;font-size:28px;font-weight:650;letter-spacing:-.03em">' + esc(fmtMoney(q.totalShippingCost, "INR")) + "</span></p>";
    }).catch(function (err) {
      box.innerHTML = '<div class="alert alert--error">' + icon("alert-circle") + "<span>Quote unavailable: " + esc(err.message) + "</span></div>";
    });
  }

  function viewNotifications() {
    document.title = "Notifications | Courier Parcel Tracking";
    return '<div class="page page--narrow">' +
      '<div class="page-head"><p class="page-head__eyebrow">Customer</p><h1>Notifications</h1>' +
      '<p class="page-head__sub">Status changes and payment updates for your parcels.</p></div>' +
      '<div class="card"><div class="card__head"><h2>' + icon("bell") + "Current</h2>" +
        '<div class="row"><button class="btn btn--secondary btn--sm" type="button" id="toggleHistory" aria-expanded="false">Show history</button>' +
        '<button class="btn btn--secondary btn--sm" type="button" id="clearNotes">Archive all</button></div></div>' +
        '<div class="card__body"><div id="noteList">' + skeletonRows(4) + "</div>" +
        '<div id="historyWrap" class="hidden" style="margin-top:24px"><h3 class="label-eyebrow" style="margin-bottom:12px">Archived</h3><div id="historyList"></div></div>' +
        "</div></div></div>";
  }

  function loadNotifications() {
    api("/notifications/mine").then(function (list) {
      $("#noteList").innerHTML = list && list.length
        ? '<ul class="note-list">' + list.map(function (n) { return noteItem(n, true); }).join("") + "</ul>"
        : emptyState("No notifications", "You will see an update here whenever one of your parcels changes status.", "bell");
    }).catch(function (err) {
      $("#noteList").innerHTML = '<div class="alert alert--error">' + icon("alert-circle") + "<span>Could not load notifications: " + esc(err.message) + "</span></div>";
    });
  }

  function loadNotificationHistory() {
    api("/notifications/history").then(function (list) {
      $("#historyList").innerHTML = list && list.length
        ? '<ul class="note-list">' + list.map(function (n) { return noteItem(n, false); }).join("") + "</ul>"
        : emptyState("Nothing archived", "Archived notifications will be listed here.", "inbox");
    }).catch(function (err) {
      $("#historyList").innerHTML = '<div class="alert alert--error">' + icon("alert-circle") + "<span>Could not load history: " + esc(err.message) + "</span></div>";
    });
  }

  function viewSupport() {
    document.title = "Support | Courier Parcel Tracking";
    return '<div class="page page--narrow">' +
      '<div class="page-head"><p class="page-head__eyebrow">Customer</p><h1>Contact support</h1>' +
      '<p class="page-head__sub">Send the support team a message about a parcel or your booking.</p></div>' +
      '<div class="card"><div class="card__body">' +
      '<form id="supportForm" class="stack">' +
        '<div class="field"><label for="sup-name">Your name</label>' +
          '<input id="sup-name" name="name" required readonly value="' + esc(profile ? profile.name : "") + '" /></div>' +
        '<div class="field"><label for="sup-email">Your email</label>' +
          '<input id="sup-email" name="email" type="email" required readonly value="' + esc(profile ? profile.email : "") + '" /></div>' +
        '<div class="field"><label for="sup-tid">Tracking ID (optional)</label>' +
          '<input id="sup-tid" name="trackingId" maxlength="80" placeholder="CP000000000000" /></div>' +
        '<div class="field"><label for="sup-msg">Message</label>' +
          '<textarea id="sup-msg" name="message" rows="6" maxlength="4000" required placeholder="Describe what happened"></textarea></div>' +
        '<button class="btn btn--primary btn--block" type="submit">' + icon("send", "icon-sm") + "Send message</button>" +
      "</form>" +
      '<p class="small muted" style="margin-top:16px">Messages are delivered to the support inbox by email.</p>' +
      "</div></div></div>";
  }

  function viewAccount() {
    document.title = "Account | Courier Parcel Tracking";
    return '<div class="page page--narrow">' +
      '<div class="page-head"><p class="page-head__eyebrow">Account</p><h1>Your account</h1></div>' +
      '<div class="card"><div class="card__body" id="accountBody">' + skeletonRows(4) + "</div></div></div>";
  }

  function loadAccount() {
    api("/auth/login").then(function (u) {
      profile = u;
      renderNav();
      $("#accountBody").innerHTML =
        detailGrid([
          ["Name", u.name],
          ["Email", u.email],
          ["Role", ROLE_LABEL[u.role] || u.role],
          ["Account ID", String(u.id)]
        ]) +
        '<div class="alert alert--info" style="margin-top:24px">' + icon("info") +
        "<span>Your session is stored for this browser tab only. Signing out or closing the tab clears it.</span></div>";
    }).catch(function (err) {
      $("#accountBody").innerHTML = '<div class="alert alert--error">' + icon("alert-circle") + "<span>Could not load your account: " + esc(err.message) + "</span></div>";
    });
  }

  /* ------------------------------------------------------------------ */
  /* Views: courier                                                      */
  /* ------------------------------------------------------------------ */

  function viewDeliveries() {
    document.title = "My deliveries | Courier Parcel Tracking";
    return '<div class="page page--wide">' +
      '<div class="page-head"><p class="page-head__eyebrow">Courier</p><h1>My deliveries</h1>' +
      '<p class="page-head__sub">Parcels assigned to you. Select one to record a status update.</p></div>' +
      '<div class="grid-auto">' +
        '<div class="card"><div class="card__head"><h2>' + icon("truck") + "Assigned parcels</h2>" +
          '<button class="btn btn--secondary btn--sm" type="button" id="refreshDeliveries">' + icon("refresh", "icon-sm") + "Refresh</button></div>" +
          '<div class="card__body" id="deliveryList">' + skeletonList(3) + "</div></div>" +
        '<div class="card"><div class="card__head"><h2>' + icon("clipboard") + "Update status</h2></div>" +
          '<div class="card__body">' +
          '<form id="statusForm" class="stack">' +
            '<div class="field"><label for="st-tid">Tracking ID</label>' +
              '<input id="st-tid" name="trackingId" required placeholder="CP000000000000" /></div>' +
            '<div class="field"><label for="st-status">New status</label>' +
              '<select id="st-status" name="status">' +
                COURIER_STATUSES.map(function (s) {
                  return '<option value="' + esc(s) + '">' + esc(STATUS_LABEL[s]) + "</option>";
                }).join("") +
              "</select></div>" +
            '<div class="field"><label for="st-note">Update note</label>' +
              '<input id="st-note" name="note" required placeholder="Collected from the sender" /></div>' +
            '<button class="btn btn--primary btn--block" type="submit">Save status</button>' +
          "</form>" +
          '<p class="small muted" style="margin-top:16px">Only parcels assigned to you can be updated.</p>' +
          "</div></div>" +
      "</div></div>";
  }

  function loadDeliveries() {
    var box = $("#deliveryList");
    if (!box) return;
    api("/delivery/mine").then(function (list) {
      if (!list || !list.length) {
        box.innerHTML = emptyState("No parcels assigned", "An operations admin assigns parcels to you. They will appear here once assigned.", "truck");
        return;
      }
      box.innerHTML = '<div class="ship-list">' + list.map(function (p) {
        return '<button class="ship-card" type="button" data-tid="' + esc(p.trackingId) + '">' +
          '<div class="ship-card__top">' + tid(p.trackingId) + statusBadge(p.status) + "</div>" +
          '<div class="ship-card__meta">' +
            "<span>" + icon("calendar", "icon-sm") + "ETA " + esc(fmtDate(p.expectedDelivery)) + "</span>" +
            "<span>" + icon("clock", "icon-sm") + "Last activity " + esc(fmtDate(p.lastActivity)) + "</span>" +
          "</div>" +
        "</button>";
      }).join("") + "</div>";
      $$("[data-tid]", box).forEach(function (btn) {
        btn.addEventListener("click", function () {
          $("#st-tid").value = btn.getAttribute("data-tid");
          $("#st-note").focus();
        });
      });
    }).catch(function (err) {
      box.innerHTML = '<div class="alert alert--error">' + icon("alert-circle") + "<span>Could not load deliveries: " + esc(err.message) + "</span></div>";
    });
  }

  /* ------------------------------------------------------------------ */
  /* Views: admin                                                        */
  /* ------------------------------------------------------------------ */

  function viewAdmin() {
    document.title = "Administration | Courier Parcel Tracking";
    return '<div class="page">' +
      '<div class="page-head"><p class="page-head__eyebrow">Operations</p><h1>Overview</h1>' +
      '<p class="page-head__sub">Live counts from the operational APIs.</p></div>' +
      '<div id="kpiBox" class="kpi-grid">' +
        '<div class="skeleton" style="height:96px"></div><div class="skeleton" style="height:96px"></div>' +
        '<div class="skeleton" style="height:96px"></div><div class="skeleton" style="height:96px"></div>' +
      "</div>" +
      '<div class="section"><div class="section__head"><h2>Recent activity</h2>' +
        '<a class="btn btn--secondary btn--sm" href="#/admin/activity">View all</a></div>' +
        '<div class="card"><div class="card__body" id="recentActivity">' + skeletonRows(5) + "</div></div></div>" +
    "</div>";
  }

  function loadAdminOverview() {
    Promise.all([
      api("/admin/users"), api("/admin/parcels"), api("/admin/delayed"),
      api("/admin/exceptions"), api("/admin/events/failed"), api("/admin/activities")
    ]).then(function (r) {
      var users = r[0], parcels = r[1], delayed = r[2], exceptions = r[3], failed = r[4], activities = r[5];
      $("#kpiBox").innerHTML =
        kpi("users", "Users", users.length, "All registered accounts", "") +
        kpi("package", "Parcels", parcels.length, "Booked to date", "") +
        kpi("clock", "Delayed", delayed.length, "Past expected delivery", delayed.length ? "kpi--warn" : "") +
        kpi("alert-triangle", "Exceptions", exceptions.length, "Flagged for review", exceptions.length ? "kpi--alert" : "") +
        kpi("file", "Failed events", failed.length, "Awaiting retry", failed.length ? "kpi--warn" : "");

      $("#recentActivity").innerHTML = activities && activities.length
        ? '<ul class="note-list">' + activities.slice(0, 8).map(function (a) {
            return '<li class="note"><span class="note__icon" aria-hidden="true">' + icon("activity", "icon-sm") + "</span>" +
              '<div class="note__body"><div class="note__text">' + esc(a.note || "Status updated") + " " + statusBadge(a.status) + "</div>" +
              '<div class="note__meta"><span>' + esc(a.trackingId) + "</span><span>" + esc(fmtDate(a.occurredAt)) + "</span></div></div></li>";
          }).join("") + "</ul>"
        : emptyState("No activity yet", "Status changes will be listed here as they happen.", "activity");
    }).catch(function (err) {
      $("#kpiBox").innerHTML = '<div class="alert alert--error" style="grid-column:1/-1">' + icon("alert-circle") +
        "<span>Could not load the overview: " + esc(err.message) + "</span></div>";
    });
  }

  function kpi(iconName, label, value, note, cls) {
    return '<div class="kpi ' + (cls || "") + '">' +
      '<div class="kpi__label">' + icon(iconName, "icon-sm") + esc(label) + "</div>" +
      '<div class="kpi__value">' + esc(String(value)) + "</div>" +
      '<div class="kpi__note">' + esc(note) + "</div></div>";
  }

  var adminParcelsCache = [];
  var adminCouriersCache = [];

  function viewAdminParcels() {
    document.title = "Parcels | Courier Parcel Tracking";
    return '<div class="page">' +
      '<div class="page-head"><p class="page-head__eyebrow">Operations</p><h1>Parcels</h1>' +
      '<p class="page-head__sub">Every parcel in the system, merged with its delivery record.</p></div>' +
      '<div class="card"><div class="card__head"><h2>' + icon("package") + "All parcels</h2>" +
        '<button class="btn btn--secondary btn--sm" type="button" id="refreshParcels">' + icon("refresh", "icon-sm") + "Refresh</button></div>" +
        '<div id="parcelsTable">' + skeletonRows(6) + "</div></div></div>";
  }

  function loadAdminParcels() {
    Promise.all([api("/admin/parcels"), api("/admin/deliveries"), api("/admin/users")]).then(function (r) {
      var parcels = r[0], deliveries = r[1], users = r[2];
      adminCouriersCache = users.filter(function (u) { return u.role === "COURIER"; });
      var byId = {};
      deliveries.forEach(function (d) { byId[d.trackingId] = d; });
      adminParcelsCache = parcels.map(function (p) {
        var d = byId[p.trackingId];
        return {
          trackingId: p.trackingId,
          receiverName: p.receiverName,
          receiverAddress: p.receiverAddress,
          expectedDelivery: p.expectedDelivery,
          status: d ? d.status : p.status,
          courierId: d ? d.courierId : null
        };
      });
      renderParcelsTable();
    }).catch(function (err) {
      $("#parcelsTable").innerHTML = '<div style="padding:16px"><div class="alert alert--error">' + icon("alert-circle") +
        "<span>Could not load parcels: " + esc(err.message) + "</span></div></div>";
    });
  }

  function renderParcelsTable() {
    var state = tableState.parcels || (tableState.parcels = { q: "", f: "", page: 1 });
    $("#parcelsTable").innerHTML = dataTable({
      id: "parcels",
      label: "Search parcels",
      searchPlaceholder: "Search tracking ID or receiver",
      rows: adminParcelsCache,
      searchState: state,
      searchKeys: ["trackingId", "receiverName", "receiverAddress"],
      filterKey: "status",
      filters: [{
        key: "status", label: "Status", allLabel: "All statuses",
        options: Object.keys(STATUS_LABEL).map(function (s) { return { value: s, label: STATUS_LABEL[s] }; })
      }],
      emptyTitle: "No parcels",
      emptyBody: "Booked parcels will be listed here.",
      emptyIcon: "package",
      cols: [
        { label: "Tracking ID", render: function (r) { return tid(r.trackingId); } },
        { label: "Status", render: function (r) { return statusBadge(r.status); } },
        { label: "Receiver", render: function (r) { return esc(r.receiverName); } },
        { label: "Expected", render: function (r) { return esc(fmtDate(r.expectedDelivery)); } },
        {
          label: "Courier", render: function (r) {
            if (!r.courierId) return '<span class="muted">Unassigned</span>';
            var c = null;
            for (var i = 0; i < adminCouriersCache.length; i++) {
              if (String(adminCouriersCache[i].id) === String(r.courierId)) { c = adminCouriersCache[i]; break; }
            }
            return esc(c ? c.name : "#" + r.courierId);
          }
        }
      ]
    });
    wireTable($("#parcelsTable"), "parcels", renderParcelsTable);
  }

  function viewAdminAssignments() {
    document.title = "Assignments | Courier Parcel Tracking";
    return '<div class="page page--wide">' +
      '<div class="page-head"><p class="page-head__eyebrow">Operations</p><h1>Assignments</h1>' +
      '<p class="page-head__sub">Assign a parcel to a courier, or create a new courier account.</p></div>' +
      '<div class="grid-auto">' +
        '<div class="card"><div class="card__head"><h2>' + icon("route") + "Assign a parcel</h2></div>" +
          '<div class="card__body"><form id="assignForm" class="stack">' +
            '<div class="field"><label for="as-parcel">Parcel</label><select id="as-parcel" name="trackingId" required></select></div>' +
            '<div class="field"><label for="as-courier">Courier</label><select id="as-courier" name="courierId" required></select></div>' +
            '<button class="btn btn--primary btn--block" type="submit">Assign parcel</button>' +
          "</form>" +
          '<p class="small muted" style="margin-top:16px">Couriers can only update parcels assigned to them.</p>' +
          "</div></div>" +
        '<div class="card"><div class="card__head"><h2>' + icon("plus") + "Create courier</h2></div>" +
          '<div class="card__body"><form id="courierForm" class="stack">' +
            '<div class="field"><label for="cq-name">Name</label><input id="cq-name" name="name" required /></div>' +
            '<div class="field"><label for="cq-email">Email</label><input id="cq-email" name="email" type="email" required /></div>' +
            '<div class="field"><label for="cq-pw">Password</label>' +
              '<input id="cq-pw" name="password" type="password" minlength="8" required autocomplete="new-password" />' +
              '<span class="field__hint">At least 8 characters.</span></div>' +
            '<button class="btn btn--secondary btn--block" type="submit">Create courier account</button>' +
          "</form></div></div>" +
      "</div></div>";
  }

  function loadAssignments() {
    Promise.all([api("/admin/parcels"), api("/admin/deliveries"), api("/admin/users")]).then(function (r) {
      var parcels = r[0], deliveries = r[1], users = r[2];
      adminCouriersCache = users.filter(function (u) { return u.role === "COURIER"; });
      var byId = {};
      deliveries.forEach(function (d) { byId[d.trackingId] = d; });

      var parcelSel = $("#as-parcel");
      parcelSel.innerHTML = parcels.length
        ? parcels.map(function (p) {
            var d = byId[p.trackingId];
            var status = d ? d.status : p.status;
            var who = d && d.courierId ? " · assigned" : " · unassigned";
            return '<option value="' + esc(p.trackingId) + '">' + esc(p.trackingId) + " · " + esc(STATUS_LABEL[status] || status) + esc(who) + "</option>";
          }).join("")
        : '<option value="">No parcels available</option>';

      var courierSel = $("#as-courier");
      courierSel.innerHTML = adminCouriersCache.length
        ? adminCouriersCache.map(function (u) {
            return '<option value="' + esc(u.id) + '">' + esc(u.name) + " (" + esc(u.email) + ")</option>";
          }).join("")
        : '<option value="">No couriers yet. Create one opposite.</option>';
    }).catch(function (err) {
      toast("Could not load assignment data: " + err.message, "error");
    });
  }

  function viewAdminUsers() {
    document.title = "Users | Courier Parcel Tracking";
    return '<div class="page">' +
      '<div class="page-head"><p class="page-head__eyebrow">Operations</p><h1>Users</h1>' +
      '<p class="page-head__sub">All registered customer, courier and administrator accounts.</p></div>' +
      '<div class="card"><div class="card__head"><h2>' + icon("users") + "Accounts</h2>" +
        '<button class="btn btn--secondary btn--sm" type="button" id="refreshUsers">' + icon("refresh", "icon-sm") + "Refresh</button></div>" +
        '<div id="usersTable">' + skeletonRows(6) + "</div></div></div>";
  }

  function loadAdminUsers() {
    api("/admin/users").then(function (users) {
      var state = tableState.users || (tableState.users = { q: "", f: "", page: 1 });
      $("#usersTable").innerHTML = dataTable({
        id: "users",
        label: "Search users",
        searchPlaceholder: "Search name or email",
        rows: users,
        searchState: state,
        searchKeys: ["name", "email"],
        filterKey: "role",
        filters: [{
          key: "role", label: "Role", allLabel: "All roles",
          options: ["CUSTOMER", "COURIER", "ADMIN"].map(function (s) { return { value: s, label: ROLE_LABEL[s] }; })
        }],
        emptyTitle: "No users",
        emptyBody: "Registered accounts will be listed here.",
        emptyIcon: "users",
        cols: [
          { label: "ID", render: function (r) { return '<span class="mono">' + esc(r.id) + "</span>"; } },
          { label: "Name", render: function (r) { return esc(r.name); } },
          { label: "Email", render: function (r) { return esc(r.email); } },
          { label: "Role", render: function (r) { return roleBadge(r.role); } }
        ]
      });
      wireTable($("#usersTable"), "users", loadAdminUsers);
    }).catch(function (err) {
      $("#usersTable").innerHTML = '<div style="padding:16px"><div class="alert alert--error">' + icon("alert-circle") +
        "<span>Could not load users: " + esc(err.message) + "</span></div></div>";
    });
  }

  function viewAdminPayments() {
    document.title = "Payments | Courier Parcel Tracking";
    return '<div class="page">' +
      '<div class="page-head"><p class="page-head__eyebrow">Operations</p><h1>Payments</h1>' +
      '<p class="page-head__sub">All payment records. Online methods are simulated in this deployment.</p></div>' +
      '<div class="card"><div class="card__head"><h2>' + icon("credit-card") + "Transactions</h2>" +
        '<button class="btn btn--secondary btn--sm" type="button" id="refreshPayments">' + icon("refresh", "icon-sm") + "Refresh</button></div>" +
        '<div id="paymentsTable">' + skeletonRows(6) + "</div></div></div>";
  }

  function loadAdminPayments() {
    api("/payments/admin/all").then(function (payments) {
      var state = tableState.payments || (tableState.payments = { q: "", f: "", page: 1 });
      $("#paymentsTable").innerHTML = dataTable({
        id: "payments",
        label: "Search payments",
        searchPlaceholder: "Search tracking ID or reference",
        rows: payments,
        searchState: state,
        searchKeys: ["trackingId", "transactionReference", "method"],
        filterKey: "status",
        filters: [{
          key: "status", label: "Status", allLabel: "All statuses",
          options: ["PENDING", "SUCCESS", "FAILED", "CASH_ON_DELIVERY"].map(function (s) {
            return { value: s, label: PAYMENT_STATUS_LABEL[s] };
          })
        }],
        emptyTitle: "No payments",
        emptyBody: "Payment records will appear once parcels are booked.",
        emptyIcon: "credit-card",
        cols: [
          { label: "Tracking ID", render: function (r) { return tid(r.trackingId); } },
          { label: "Amount", num: true, render: function (r) { return esc(fmtMoney(r.amount, r.currency)); } },
          { label: "Method", render: function (r) { return esc(r.method ? r.method.replace(/_/g, " ") : "Not chosen"); } },
          { label: "Status", render: function (r) { return paymentBadge(r.status); } },
          { label: "Reference", render: function (r) { return '<span class="mono">' + esc(r.transactionReference || "-") + "</span>"; } },
          { label: "Created", render: function (r) { return esc(fmtDate(r.createdAt)); } }
        ]
      });
      wireTable($("#paymentsTable"), "payments", loadAdminPayments);
    }).catch(function (err) {
      $("#paymentsTable").innerHTML = '<div style="padding:16px"><div class="alert alert--error">' + icon("alert-circle") +
        "<span>Could not load payments: " + esc(err.message) + "</span></div></div>";
    });
  }

  function viewAdminActivity() {
    document.title = "Activity | Courier Parcel Tracking";
    return '<div class="page">' +
      '<div class="page-head"><p class="page-head__eyebrow">Operations</p><h1>Activity</h1>' +
      '<p class="page-head__sub">Every recorded status change across all parcels.</p></div>' +
      '<div class="card"><div class="card__head"><h2>' + icon("activity") + "History</h2>" +
        '<button class="btn btn--secondary btn--sm" type="button" id="refreshActivity">' + icon("refresh", "icon-sm") + "Refresh</button></div>" +
        '<div id="activityTable">' + skeletonRows(6) + "</div></div></div>";
  }

  function loadAdminActivity() {
    api("/admin/activities").then(function (rows) {
      var state = tableState.activity || (tableState.activity = { q: "", f: "", page: 1 });
      $("#activityTable").innerHTML = dataTable({
        id: "activity",
        label: "Search activity",
        searchPlaceholder: "Search tracking ID or note",
        rows: rows,
        searchState: state,
        searchKeys: ["trackingId", "note"],
        filterKey: "status",
        filters: [{
          key: "status", label: "Status", allLabel: "All statuses",
          options: Object.keys(STATUS_LABEL).map(function (s) { return { value: s, label: STATUS_LABEL[s] }; })
        }],
        emptyTitle: "No activity recorded",
        emptyBody: "Status changes will appear here.",
        emptyIcon: "activity",
        cols: [
          { label: "Tracking ID", render: function (r) { return tid(r.trackingId); } },
          { label: "Status", render: function (r) { return statusBadge(r.status); } },
          { label: "Note", render: function (r) { return esc(r.note || "-"); } },
          { label: "When", render: function (r) { return esc(fmtDate(r.occurredAt)); } }
        ]
      });
      wireTable($("#activityTable"), "activity", loadAdminActivity);
    }).catch(function (err) {
      $("#activityTable").innerHTML = '<div style="padding:16px"><div class="alert alert--error">' + icon("alert-circle") +
        "<span>Could not load activity: " + esc(err.message) + "</span></div></div>";
    });
  }

  function viewAdminEvents() {
    document.title = "Failed events | Courier Parcel Tracking";
    return '<div class="page">' +
      '<div class="page-head"><p class="page-head__eyebrow">Operations</p><h1>Failed events</h1>' +
      '<p class="page-head__sub">Message queue events that could not be processed and are awaiting a retry.</p></div>' +
      '<div class="card"><div class="card__head"><h2>' + icon("alert-triangle") + "Dead letter queue</h2>" +
        '<button class="btn btn--secondary btn--sm" type="button" id="refreshEvents">' + icon("refresh", "icon-sm") + "Refresh</button></div>" +
        '<div id="eventsTable">' + skeletonRows(5) + "</div></div></div>";
  }

  function loadAdminEvents() {
    api("/admin/events/failed").then(function (rows) {
      var state = tableState.events || (tableState.events = { q: "", f: "", page: 1 });
      $("#eventsTable").innerHTML = dataTable({
        id: "events",
        label: "Search failed events",
        searchPlaceholder: "Search payload",
        rows: rows,
        searchState: state,
        searchKeys: ["payload"],
        emptyTitle: "No failed events",
        emptyBody: "Every message has been processed successfully.",
        emptyIcon: "check-circle",
        cols: [
          { label: "ID", render: function (r) { return '<span class="mono">' + esc(r.id) + "</span>"; } },
          { label: "Failed at", render: function (r) { return esc(fmtDate(r.failedAt)); } },
          { label: "Attempts", num: true, render: function (r) { return esc(String(r.attempts)); } },
          { label: "Payload", render: function (r) { return '<code class="mono">' + esc(String(r.payload).slice(0, 90)) + "</code>"; } }
        ]
      });
      wireTable($("#eventsTable"), "events", loadAdminEvents);
    }).catch(function (err) {
      $("#eventsTable").innerHTML = '<div style="padding:16px"><div class="alert alert--error">' + icon("alert-circle") +
        "<span>Could not load failed events: " + esc(err.message) + "</span></div></div>";
    });
  }

  function viewAdminEmails() {
    document.title = "Email deliveries | Courier Parcel Tracking";
    return '<div class="page">' +
      '<div class="page-head"><p class="page-head__eyebrow">Operations</p><h1>Email deliveries</h1>' +
      '<p class="page-head__sub">Every notification email the system attempted, with the SMTP result. Credentials are redacted in error details.</p></div>' +
      '<div class="card"><div class="card__head"><h2>' + icon("mail") + "Delivery log</h2>" +
        '<button class="btn btn--secondary btn--sm" type="button" id="refreshEmails">' + icon("refresh", "icon-sm") + "Refresh</button></div>" +
        '<div id="emailsTable">' + skeletonRows(5) + "</div></div></div>";
  }

  function loadAdminEmails() {
    api("/admin/email-deliveries").then(function (rows) {
      var state = tableState.emails || (tableState.emails = { q: "", f: "", page: 1 });
      $("#emailsTable").innerHTML = dataTable({
        id: "emails",
        label: "Search email deliveries",
        searchPlaceholder: "Search recipient or tracking ID",
        rows: rows,
        searchState: state,
        searchKeys: ["recipient", "trackingId", "subject", "eventType", "errorDetails"],
        filterKey: "status",
        filters: [{
          key: "status", label: "Status", allLabel: "All statuses",
          options: [{ value: "SENT", label: "Sent" }, { value: "FAILED", label: "Failed" }]
        }],
        emptyTitle: "No emails attempted",
        emptyBody: rows.length === 0
          ? "Nothing has been queued yet. Email is only attempted when MAIL_ENABLED is true and a parcel is booked or paid."
          : "Nothing to show.",
        emptyIcon: "mail",
        cols: [
          { label: "Recipient", render: function (r) { return esc(r.recipient || "-"); } },
          { label: "Type", render: function (r) { return esc(String(r.eventType || "-").replace(/_/g, " ")); } },
          { label: "Status", render: function (r) { return '<span class="badge badge--' + esc(r.status === "SENT" ? "SUCCESS" : "FAILED") + '">' + esc(r.status) + "</span>"; } },
          { label: "Attempts", num: true, render: function (r) { return esc(String(r.attempts)); } },
          { label: "Error", render: function (r) { return r.errorDetails ? '<span class="small">' + esc(r.errorDetails) + "</span>" : '<span class="muted">-</span>'; } },
          { label: "Updated", render: function (r) { return esc(fmtDate(r.updatedAt)); } }
        ]
      });
      wireTable($("#emailsTable"), "emails", loadAdminEmails);
    }).catch(function (err) {
      $("#emailsTable").innerHTML = '<div style="padding:16px"><div class="alert alert--error">' + icon("alert-circle") +
        "<span>Could not load email deliveries: " + esc(err.message) + "</span></div></div>";
    });
  }

  /* ------------------------------------------------------------------ */
  /* 404                                                                 */
  /* ------------------------------------------------------------------ */

  function viewNotFound() {
    document.title = "Page not found | Courier Parcel Tracking";
    return '<div class="page page--narrow">' +
      '<div class="page-head"><p class="page-head__eyebrow">404</p><h1>Page not found</h1>' +
      '<p class="page-head__sub">That address does not match any page in this application.</p></div>' +
      '<div class="card"><div class="card__body">' +
        emptyState("Nothing at this address", "Check the link, or start again from the tracking page.", "search") +
        '<div class="row" style="margin-top:20px">' +
          '<a class="btn btn--primary" href="#/">Track a shipment</a>' +
          (profile ? '<a class="btn btn--secondary" href="#/account">Your account</a>'
                   : '<a class="btn btn--secondary" href="#/signin">Sign in</a>') +
        "</div>" +
      "</div></div></div>";
  }

  function viewDenied(needed) {
    document.title = "Access denied | Courier Parcel Tracking";
    return '<div class="page page--narrow">' +
      '<div class="page-head"><p class="page-head__eyebrow">403</p><h1>Access denied</h1></div>' +
      '<div class="card"><div class="card__body">' +
        '<div class="alert alert--warn">' + icon("shield") + "<span>This page is only available to " + esc(needed) + " accounts. You are signed in as " +
        esc(ROLE_LABEL[profile && profile.role] || "a guest") + ".</span></div>" +
        '<div class="row" style="margin-top:20px"><a class="btn btn--primary" href="#/">Back to tracking</a></div>' +
      "</div></div></div>";
  }

  /* ------------------------------------------------------------------ */
  /* Router                                                              */
  /* ------------------------------------------------------------------ */

  var ROUTES = [
    { path: "/", roles: null, view: viewLanding },
    { path: "/signin", roles: null, view: viewSignIn, guestOnly: true },
    { path: "/register", roles: null, view: viewRegister, guestOnly: true },
    { path: "/shipments", roles: ["CUSTOMER"], view: viewShipments, after: loadShipments },
    { path: "/book", roles: ["CUSTOMER"], view: viewBook },
    { path: "/notifications", roles: ["CUSTOMER"], view: viewNotifications, after: loadNotifications },
    { path: "/support", roles: ["CUSTOMER"], view: viewSupport },
    { path: "/account", roles: null, view: viewAccount, after: loadAccount },
    { path: "/deliveries", roles: ["COURIER"], view: viewDeliveries, after: loadDeliveries },
    { path: "/admin", roles: ["ADMIN"], view: viewAdmin, after: loadAdminOverview },
    { path: "/admin/parcels", roles: ["ADMIN"], view: viewAdminParcels, after: loadAdminParcels },
    { path: "/admin/assignments", roles: ["ADMIN"], view: viewAdminAssignments, after: loadAssignments },
    { path: "/admin/users", roles: ["ADMIN"], view: viewAdminUsers, after: loadAdminUsers },
    { path: "/admin/payments", roles: ["ADMIN"], view: viewAdminPayments, after: loadAdminPayments },
    { path: "/admin/activity", roles: ["ADMIN"], view: viewAdminActivity, after: loadAdminActivity },
    { path: "/admin/events", roles: ["ADMIN"], view: viewAdminEvents, after: loadAdminEvents },
    { path: "/admin/emails", roles: ["ADMIN"], view: viewAdminEmails, after: loadAdminEmails }
  ];

  function formData(form) {
    var out = {};
    var entries = new FormData(form).entries();
    var step = entries.next();
    while (!step.done) {
      out[step.value[0]] = step.value[1];
      step = entries.next();
    }
    return out;
  }

  function render() {
    var path = currentHash();
    if (!path) return; /* in-page anchor such as #main */
    var main = $("#main");
    navOpen = false;

    var match = null;
    var detailId = null;
    for (var i = 0; i < ROUTES.length; i++) {
      if (ROUTES[i].path === path) { match = ROUTES[i]; break; }
    }
    if (!match && /^\/shipments\/[^/]+$/.test(path)) {
      match = { path: "/shipments/:id", roles: ["CUSTOMER"], view: function () { return viewShipmentDetail(detailId); }, after: function () { loadShipmentDetail(detailId); } };
      detailId = decodeURIComponent(path.slice("/shipments/".length));
    }

    if (!match) {
      main.innerHTML = viewNotFound();
      renderNav();
      window.scrollTo(0, 0);
      return;
    }
    if (match.guestOnly && profile) { go(profile.role === "ADMIN" ? "/admin" : (profile.role === "COURIER" ? "/deliveries" : "/shipments")); return; }
    if (match.roles && (!profile || match.roles.indexOf(profile.role) === -1)) {
      if (!profile) {
        toast("Please sign in to continue.", "error");
        go("/signin");
        return;
      }
      main.innerHTML = viewDenied(match.roles.map(function (r) { return ROLE_LABEL[r].toLowerCase(); }).join(" or "));
      renderNav();
      window.scrollTo(0, 0);
      return;
    }

    main.innerHTML = match.view();
    renderNav();
    window.scrollTo(0, 0);
    if (match.after) match.after();
    wireCommon();
  }

  /* ------------------------------------------------------------------ */
  /* Global wiring                                                       */
  /* ------------------------------------------------------------------ */

  function wireCommon() {
    $$("[data-pw-toggle]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var input = document.getElementById(btn.getAttribute("data-pw-toggle"));
        var show = input.type === "password";
        input.type = show ? "text" : "password";
        btn.textContent = show ? "Hide" : "Show";
        btn.setAttribute("aria-pressed", String(show));
      });
    });

    var sf = $("#signinForm");
    if (sf) sf.addEventListener("submit", onSignIn);

    var rf = $("#registerForm");
    if (rf) rf.addEventListener("submit", onRegister);

    var tf = $("#trackForm");
    if (tf) tf.addEventListener("submit", onTrack);

    var bf = $("#bookForm");
    if (bf) {
      var t;
      bf.addEventListener("input", function () { window.clearTimeout(t); t = window.setTimeout(loadQuote, 350); });
      $("#b-speed").addEventListener("change", loadQuote);
      bf.addEventListener("submit", onBook);
    }

    var sup = $("#supportForm");
    if (sup) sup.addEventListener("submit", onSupport);

    var sfm = $("#statusForm");
    if (sfm) sfm.addEventListener("submit", onStatusUpdate);

    var af = $("#assignForm");
    if (af) af.addEventListener("submit", onAssign);

    var cf = $("#courierForm");
    if (cf) cf.addEventListener("submit", onCreateCourier);

    bind("#refreshShipments", loadShipments);
    bind("#refreshDeliveries", loadDeliveries);
    bind("#refreshParcels", loadAdminParcels);
    bind("#refreshUsers", loadAdminUsers);
    bind("#refreshPayments", loadAdminPayments);
    bind("#refreshActivity", loadAdminActivity);
    bind("#refreshEvents", loadAdminEvents);
    bind("#refreshEmails", loadAdminEmails);

    var th = $("#toggleHistory");
    if (th) th.addEventListener("click", function () {
      var wrap = $("#historyWrap");
      var opening = wrap.classList.contains("hidden");
      wrap.classList.toggle("hidden", !opening);
      th.setAttribute("aria-expanded", String(opening));
      th.textContent = opening ? "Hide history" : "Show history";
      if (opening) loadNotificationHistory();
    });

    var cn = $("#clearNotes");
    if (cn) cn.addEventListener("click", function () {
      busy(cn, function () {
        return api("/notifications/mine/clear", { method: "POST" }).then(function (r) {
          toast(r.movedToHistory + " notification(s) archived.");
          loadNotifications();
        });
      }).catch(function (err) { toast("Could not archive notifications: " + err.message, "error"); });
    });
  }

  function bind(sel, fn) {
    var el = $(sel);
    if (el) el.addEventListener("click", function () {
      busy(el, fn).catch(function (err) { toast(err.message, "error"); });
    });
  }

  /* ------------------------------------------------------------------ */
  /* Form handlers                                                       */
  /* ------------------------------------------------------------------ */

  function onSignIn(e) {
    e.preventDefault();
    var form = e.currentTarget;
    var d = formData(form);
    busy(form.querySelector("[type=submit]"), function () {
      var creds = { email: d.email.trim().toLowerCase(), password: d.password };
      return api("/auth/login", { headers: { Authorization: "Basic " + window.btoa(creds.email + ":" + creds.password) } })
        .then(function (u) {
          saveSession(creds);
          profile = u;
          toast("Signed in as " + u.name + ".");
          go(u.role === "ADMIN" ? "/admin" : (u.role === "COURIER" ? "/deliveries" : "/shipments"));
        });
    }).catch(function (err) {
      saveSession(null);
      profile = null;
      toast("Sign in failed: " + err.message, "error");
    });
  }

  function onRegister(e) {
    e.preventDefault();
    var form = e.currentTarget;
    var d = formData(form);
    busy(form.querySelector("[type=submit]"), function () {
      return api("/auth/register", { method: "POST", body: JSON.stringify({ name: d.name, email: d.email, password: d.password }) })
        .then(function (u) {
          toast("Account created for " + u.email + ". Sign in to continue.");
          form.reset();
          go("/signin");
        });
    }).catch(function (err) { toast("Registration failed: " + err.message, "error"); });
  }

  function onTrack(e) {
    e.preventDefault();
    var form = e.currentTarget;
    var id = formData(form).id.trim();
    var box = $("#trackResult");
    busy(form.querySelector("[type=submit]"), function () {
      box.innerHTML = skeletonRows(5);
      return api("/tracking/" + encodeURIComponent(id)).then(function (p) {
        box.innerHTML =
          '<div class="card"><div class="card__body">' +
            '<div class="row row--between" style="margin-bottom:16px">' + tid(p.trackingId, true) + statusBadge(p.status) + "</div>" +
            detailGrid([
              ["Expected delivery", fmtDate(p.expectedDelivery)],
              ["Events recorded", String((p.history || []).length)]
            ]) +
            '<div class="section"><div class="section__head"><h2>Progress</h2></div>' + pipeline(p.status) + "</div>" +
            '<div class="section"><div class="section__head"><h2>History</h2></div>' + timeline(p.history) + "</div>" +
          "</div></div>";
      });
    }).catch(function (err) {
      box.innerHTML = '<div class="alert alert--error">' + icon("alert-circle") +
        "<span>No shipment found for " + esc(id) + ". " + esc(err.message) + "</span></div>";
    });
  }

  function onBook(e) {
    e.preventDefault();
    var form = e.currentTarget;
    var d = formData(form);
    if (d.expectedDelivery) d.expectedDelivery = new Date(d.expectedDelivery).toISOString();
    else delete d.expectedDelivery;
    d.weightKg = Number(d.weightKg);
    d.distanceKm = Number(d.distanceKm);
    busy(form.querySelector("[type=submit]"), function () {
      return api("/parcels", { method: "POST", body: JSON.stringify(d) }).then(function (p) {
        toast("Booking created. Tracking ID " + p.trackingId + ".");
        form.reset();
        $("#quoteBox").innerHTML = emptyState("No quote yet", "Enter weight, distance and both addresses to calculate the shipping cost.", "scale");
        return showPayment(p.trackingId);
      });
    }).catch(function (err) { toast("Booking failed: " + err.message, "error"); });
  }

  function showPayment(trackingId) {
    var box = $("#bookResult");
    box.innerHTML = '<div class="section"><div class="section__head"><h2>Payment</h2></div>' + skeletonRows(3) + "</div>";
    var tries = 0;
    function attempt() {
      return api("/payments/" + encodeURIComponent(trackingId)).then(function (order) {
        if (!order && tries < 10) { tries++; return new Promise(function (r) { window.setTimeout(r, 600); }).then(attempt); }
        if (!order) throw new Error("The payment order was not created in time.");
        if (order.status === "SUCCESS" || order.status === "CASH_ON_DELIVERY") {
          box.innerHTML = '<div class="section"><div class="section__head"><h2>Payment</h2></div>' +
            '<div class="alert alert--success">' + icon("check-circle") +
            "<span>" + esc(order.status === "SUCCESS" ? "Payment recorded." : "Cash on delivery selected.") +
            " " + esc(fmtMoney(order.amount, order.currency)) + " via " + esc(String(order.method).replace(/_/g, " ")) + ".</span></div>" +
            '<p class="small muted" style="margin-top:12px">Reference ' + esc(order.transactionReference) + ". Online payments are simulated in this deployment.</p></div>";
          return;
        }
        box.innerHTML = '<div class="section"><div class="section__head"><h2>Payment</h2></div>' +
          '<p class="small muted">' + esc(fmtMoney(order.amount, order.currency)) + " due for " + tid(trackingId) + "</p>" +
          '<form id="payForm" class="stack" style="margin-top:16px;max-width:360px">' +
            '<div class="field"><label for="pay-method">Payment method</label>' +
              '<select id="pay-method" name="method">' +
                '<option value="UPI">UPI</option><option value="CARD">Card</option>' +
                '<option value="WALLET">Wallet</option><option value="CASH_ON_DELIVERY">Cash on delivery</option>' +
              "</select></div>" +
            '<button class="btn btn--primary" type="submit">Confirm payment</button>' +
          "</form></div>";
        $("#payForm").addEventListener("submit", function (ev) {
          ev.preventDefault();
          busy(ev.target.querySelector("[type=submit]"), function () {
            return api("/payments/" + encodeURIComponent(trackingId) + "/pay", {
              method: "POST", body: JSON.stringify(formData(ev.target))
            }).then(function (paid) { showPayment(trackingId); return paid; });
          }).then(function () { toast("Payment recorded for " + trackingId + "."); })
            .catch(function (err) { toast("Payment failed: " + err.message, "error"); });
        });
      });
    }
    attempt().catch(function (err) {
      box.innerHTML = '<div class="alert alert--error">' + icon("alert-circle") +
        "<span>Could not load the payment order: " + esc(err.message) + "</span></div>";
    });
  }

  function onSupport(e) {
    e.preventDefault();
    var form = e.currentTarget;
    var d = formData(form);
    busy(form.querySelector("[type=submit]"), function () {
      return api("/support/contact", {
        method: "POST",
        body: JSON.stringify({ name: d.name, email: d.email, trackingId: d.trackingId || null, message: d.message })
      }).then(function (r) {
        toast(r.message, "success");
        form.reset();
        $("#sup-name").value = profile ? profile.name : "";
        $("#sup-email").value = profile ? profile.email : "";
      });
    }).catch(function (err) { toast("Support message could not be sent: " + err.message, "error"); });
  }

  function onStatusUpdate(e) {
    e.preventDefault();
    var form = e.currentTarget;
    var d = formData(form);
    var id = d.trackingId.trim();
    delete d.trackingId;
    busy(form.querySelector("[type=submit]"), function () {
      return api("/delivery/" + encodeURIComponent(id) + "/status", { method: "PUT", body: JSON.stringify(d) })
        .then(function () {
          toast("Status updated for " + id + ".");
          form.reset();
          loadDeliveries();
        });
    }).catch(function (err) { toast("Update failed: " + err.message, "error"); });
  }

  function onAssign(e) {
    e.preventDefault();
    var form = e.currentTarget;
    var d = formData(form);
    busy(form.querySelector("[type=submit]"), function () {
      return api("/delivery/" + encodeURIComponent(d.trackingId) + "/assignment", {
        method: "PUT", body: JSON.stringify({ courierId: Number(d.courierId) })
      }).then(function () {
        toast("Parcel " + d.trackingId + " assigned.");
        loadAssignments();
      });
    }).catch(function (err) { toast("Assignment failed: " + err.message, "error"); });
  }

  function onCreateCourier(e) {
    e.preventDefault();
    var form = e.currentTarget;
    var d = formData(form);
    busy(form.querySelector("[type=submit]"), function () {
      return api("/admin/couriers", { method: "POST", body: JSON.stringify(d) }).then(function (u) {
        toast("Courier account created for " + u.email + ".");
        form.reset();
        loadAssignments();
      });
    }).catch(function (err) { toast("Courier creation failed: " + err.message, "error"); });
  }

  /* ------------------------------------------------------------------ */
  /* Boot                                                                */
  /* ------------------------------------------------------------------ */

  $("#year").textContent = String(new Date().getFullYear());

  $("#navToggle").addEventListener("click", function () {
    navOpen = !navOpen;
    renderNav();
  });

  document.addEventListener("click", function (e) {
    var a = e.target.closest ? e.target.closest("a[href^='#']") : null;
    if (a && navOpen) { navOpen = false; renderNav(); }
  });

  window.addEventListener("hashchange", render);

  /* A real path such as /signin (no #) becomes the matching hash route. */
  (function () {
    var path = window.location.pathname.replace(/\/+$/, "").replace(/\/index\.html$/, "");
    if (!path) return;
    window.location.replace("/#" + path + window.location.search);
  })();

  if (!window.location.hash) window.location.hash = "/";
  else render();

  /* If a session was restored, refresh the profile so the nav is correct. */
  if (session) {
    api("/auth/login").then(function (u) { profile = u; renderNav(); }).catch(function () { saveSession(null); profile = null; renderNav(); });
  }
})();
