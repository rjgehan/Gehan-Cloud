const LOCATIONS = [
    { name: "Hamilton", lat: 40.22, lon: -74.65, prefix: "ham" },
    { name: "New York", lat: 40.7128, lon: -74.0060, prefix: "nyc" },
    { name: "Ashburn", lat: 39.0438, lon: -77.4874, prefix: "ash" },
    { name: "Manasquan", lat: 40.1265, lon: -74.0424, prefix: "man" }
];

const WMO_DESC = [
    [0, "Clear sky"],
    [[1, 2, 3], "Mainly/partly cloudy"],
    [[45, 48], "Fog"],
    [[51, 53, 55, 56, 57], "Drizzle"],
    [[61, 63, 65, 66, 67], "Rain"],
    [[71, 73, 75, 77], "Snow"],
    [[80, 81, 82], "Rain showers"],
    [[85, 86], "Snow showers"],
    [[95], "Thunderstorm"],
    [[96, 99], "Thunderstorm with hail"]
];

function codeToDesc(code) {
    for (const [k, v] of WMO_DESC) {
        if (Array.isArray(k) ? k.includes(code) : k === code) return v;
    }
    return "—";
}

async function loadWeatherFor(location) {
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${location.lat}&longitude=${location.lon}` +
            `&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m` +
            `&daily=sunrise,sunset` +
            `&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=America%2FNew_York`;

    try {
        const res = await fetch(url, { cache: "no-store" });
        const data = await res.json();
        const c = data.current;

        const now = new Date();
const sunrise = new Date(data.daily.sunrise[0]);
const sunset  = new Date(data.daily.sunset[0]);

const isNight = (now < sunrise || now > sunset);

const card = document.querySelector(`.flex-weather.bg-${location.prefix}`);
if (isNight) {
  card.classList.add("night");
} else {
  card.classList.remove("night");
}



        document.getElementById(`${location.prefix}-desc`).innerHTML = 
    `<i class="bi ${codeToIcon(c.weather_code, isNight)} w-mainico"></i>`;


        document.getElementById(`${location.prefix}-temp`).textContent = `${Math.round(c.temperature_2m)}°F`;
        document.getElementById(`${location.prefix}-rh`).textContent = humidityCategory(c.relative_humidity_2m);
        document.getElementById(`${location.prefix}-wind`).textContent = windCategory(c.wind_speed_10m);

    } catch (e) {
        console.error(`Weather fetch failed for ${location.name}`, e);
        document.getElementById(`${location.prefix}-desc`).textContent = "Unavailable";
    }
}

function loadAllWeather() {
    LOCATIONS.forEach(loadWeatherFor);
}

loadAllWeather();
setInterval(loadAllWeather, 10 * 60 * 1000);

const WMO_ICON = [
    [0, "bi-brightness-high"],              // clear
    [[1, 2, 3], "bi-cloud-sun"],            // partly cloudy
    [[45, 48], "bi-cloud-fog"],             // fog
    [[51, 53, 55, 56, 57], "bi-cloud-drizzle"], // drizzle
    [[61, 63, 65, 66, 67], "bi-cloud-rain"],    // rain
    [[71, 73, 75, 77], "bi-snow"],              // snow
    [[80, 81, 82], "bi-cloud-rain-heavy"],     // showers
    [[85, 86], "bi-snow2"],                    // snow showers
    [[95], "bi-lightning"],                    // thunder
    [[96, 99], "bi-cloud-lightning-rain"]      // thunder + hail
];

function codeToIcon(code, isNight) {
  for (const [k, v] of WMO_ICON) {
    if (Array.isArray(k) ? k.includes(code) : k === code) {
      // special case: clear sky (0) or partly cloudy (1–3)
      if (isNight) {
        if (code === 0) return "bi-moon-stars";   // clear night
        if ([1,2,3].includes(code)) return "bi-cloud-moon"; // cloudy night
      }
      return v;
    }
  }
  return "bi-question-circle"; // fallback
}


function windCategory(speed) {
    if (speed < 1) return "No wind";
    if (speed < 15) return "Light wind";
    if (speed < 30) return "Windy";
    if (speed < 50) return "Strong wind";
    return "Extreme wind";
}

function humidityCategory(rh) {
    if (rh < 20) return "Very dry";
    if (rh < 40) return "Dry";
    if (rh < 60) return "Comfortable";
    if (rh < 80) return "Humid";
    return "Very humid";
}

// --- header clock -----------------------------------------------------------
function greetingFor(hour) {
    if (hour < 5) return "Good night";
    if (hour < 12) return "Good morning";
    if (hour < 17) return "Good afternoon";
    if (hour < 21) return "Good evening";
    return "Good night";
}

function tickClock() {
    const now = new Date();
    const clock = document.getElementById("clock");
    const today = document.getElementById("today");
    const greeting = document.getElementById("greeting");
    if (!clock) return;

    clock.textContent = now.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
    today.textContent = now.toLocaleDateString([], { weekday: "long", month: "long", day: "numeric" });
    greeting.textContent = greetingFor(now.getHours());
}

tickClock();
setInterval(tickClock, 10 * 1000);

// --- app pager --------------------------------------------------------------
// Turns the single app grid into iPhone-style pages you swipe between. How many
// tiles fit on a page comes from --cols/--rows in home.css, so it re-pages
// itself when the breakpoint changes instead of hard-coding a count.

function readGridSize() {
    const root = getComputedStyle(document.documentElement);
    const cols = parseInt(root.getPropertyValue("--cols"), 10);
    const rows = parseInt(root.getPropertyValue("--rows"), 10);
    return {
        cols: Number.isFinite(cols) && cols > 0 ? cols : 4,
        rows: Number.isFinite(rows) && rows > 0 ? rows : 2
    };
}

function buildPager() {
    const grid = document.getElementById("apps");
    if (!grid) return;

    // Remember the tiles once; later rebuilds reuse these same elements.
    if (!buildPager.tiles) {
        buildPager.tiles = Array.from(grid.querySelectorAll(".app"));
    }
    const tiles = buildPager.tiles;
    if (!tiles.length) return;

    const { cols, rows } = readGridSize();
    const perPage = cols * rows;
    const pageCount = Math.ceil(tiles.length / perPage);

    // Everything fits: leave the plain grid alone.
    if (pageCount <= 1) {
        teardownPager();
        tiles.forEach(t => grid.appendChild(t));
        grid.hidden = false;
        return;
    }

    const previous = buildPager.index || 0;
    teardownPager();

    const pager = document.createElement("div");
    pager.className = "pager";
    pager.id = "pager";

    for (let i = 0; i < pageCount; i++) {
        const page = document.createElement("div");
        page.className = "page";
        page.setAttribute("role", "group");
        page.setAttribute("aria-label", "Apps page " + (i + 1) + " of " + pageCount);
        tiles.slice(i * perPage, (i + 1) * perPage).forEach(t => page.appendChild(t));
        pager.appendChild(page);
    }

    const dots = document.createElement("div");
    dots.className = "dots";
    dots.id = "dots";
    for (let i = 0; i < pageCount; i++) {
        const dot = document.createElement("button");
        dot.type = "button";
        dot.className = "dot";
        dot.setAttribute("aria-label", "Go to apps page " + (i + 1));
        dot.addEventListener("click", () => {
            pager.scrollTo({ left: pager.clientWidth * i, behavior: "smooth" });
        });
        dots.appendChild(dot);
    }

    grid.hidden = true;
    grid.after(pager);
    pager.after(dots);

    const markActive = () => {
        const i = Math.round(pager.scrollLeft / Math.max(1, pager.clientWidth));
        buildPager.index = i;
        Array.from(dots.children).forEach((d, n) => {
            d.setAttribute("aria-current", n === i ? "true" : "false");
        });
    };
    pager.addEventListener("scroll", () => {
        window.clearTimeout(buildPager.scrollTimer);
        buildPager.scrollTimer = window.setTimeout(markActive, 60);
    });

    // Stay on the page the viewer was looking at across a rebuild.
    const target = Math.min(previous, pageCount - 1);
    pager.scrollLeft = pager.clientWidth * target;
    markActive();
}

function teardownPager() {
    const old = document.getElementById("pager");
    const dots = document.getElementById("dots");
    if (old) old.remove();
    if (dots) dots.remove();
}

buildPager();

let pagerResizeTimer;
window.addEventListener("resize", function () {
    window.clearTimeout(pagerResizeTimer);
    pagerResizeTimer = window.setTimeout(buildPager, 150);
});
