// Confirmation prompts for the destructive admin actions.
//
// These used to be onclick attributes with the username interpolated into a JS
// string literal. The username pattern happens to exclude quotes, so it was not
// exploitable, but it put user-controlled text inside code and it forced the
// Content-Security-Policy to allow inline script. Reading the message from a data
// attribute keeps the text as text.
document.querySelectorAll("[data-confirm]").forEach(function (el) {
    el.addEventListener("click", function (event) {
        if (!window.confirm(el.getAttribute("data-confirm"))) {
            event.preventDefault();
        }
    });
});
