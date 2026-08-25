function changeMessage() {
    document.getElementById('message').textContent = "Updated at " + new Date().toLocaleTimeString();
}

const LOCATIONS = [
    { name: "Hamilton", lat: 40.22, lon: -74.65, prefix: "ham" },
    { name: "New York", lat: 40.7128, lon: -74.0060, prefix: "nyc" },
    { name: "Ashburn", lat: 39.0438, lon: -77.4874, prefix: "ash" },
    { name: "Ocean City", lat: 39.2794, lon: -74.5769, prefix: "man" }
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

function openPadEdit() {
    const name = prompt('Pad name to edit?');
    if (name) location.href = '/notes/p/' + encodeURIComponent(name);
}

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

