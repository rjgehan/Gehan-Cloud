// The hand-curated half of the data: what each country is called, which names
// people actually type for it, which region it belongs to, and where its capital
// is. Geometry comes from Natural Earth; everything here is plain facts written
// out by hand so the generated files carry no third-party database licence.
//
// One entry per code that appears in the generated files. build-data.mjs fails
// if a feature resolves to a code with no entry here, or if an entry here has no
// geometry, so the two can never drift apart silently.
//
// Entry shape:  CODE: c(name, region, capital, aliases, extra)
//   capital  [name, lat, lng, [capital aliases]] or null where there genuinely
//            is none (Antarctica, uninhabited islands, Hong Kong/Macao).
//   aliases  names people type that the i18n-iso-countries list lacks. The
//            build adds that package's English names on top and drops anything
//            two countries would share.
//   extra    { iso2 } for codes ISO never assigned an alpha-2 to.
//
// Choices worth knowing about (also listed in tools/globle/README.md):
//   regions follow the UN M49 scheme, except Russia → europe and Cyprus →
//     europe, as the game's contract asks. Turkey, the Caucasus and Kazakhstan
//     stay in asia; Egypt is africa; Greenland americas.
//   Bolivia → Sucre (constitutional capital; La Paz is an alias).
//   South Africa → Pretoria (executive; Cape Town and Bloemfontein are aliases).
//   Netherlands → Amsterdam (constitutional; The Hague is an alias).
//   Israel → Jerusalem. Palestine → Ramallah (where the government sits;
//     East Jerusalem, the claimed capital, is an alias) — keeping the two
//     capitals a few kilometres apart would make the capitals mode a coin toss.
//   Côte d'Ivoire → Yamoussoukro, Tanzania → Dodoma, Burundi → Gitega,
//     Benin → Porto-Novo, Sri Lanka → Sri Jayawardenepura Kotte, Eswatini →
//     Mbabane, Myanmar → Naypyidaw, Equatorial Guinea → Ciudad de la Paz
//     (decree of 2 Jan 2026): the official capital, with the better known
//     city as an alias. (Malaysia keeps Kuala Lumpur, with Putrajaya, the
//     administrative centre, as the alias.)
//   Nauru has no official capital; Yaren is the de facto seat. Montserrat's
//     official capital, Plymouth, was buried by the volcano; Brades is used.

const c = (name, region, capital = null, aliases = [], extra = {}) => ({ name, region, capital, aliases, ...extra });

/** The 193 UN member states, by ISO alpha-3. */
export const UN_MEMBERS = `
  AFG ALB DZA AND AGO ATG ARG ARM AUS AUT AZE BHS BHR BGD BRB BLR BEL BLZ BEN BTN
  BOL BIH BWA BRA BRN BGR BFA BDI CPV KHM CMR CAN CAF TCD CHL CHN COL COM COG COD
  CRI CIV HRV CUB CYP CZE DNK DJI DMA DOM ECU EGY SLV GNQ ERI EST SWZ ETH FJI FIN
  FRA GAB GMB GEO DEU GHA GRC GRD GTM GIN GNB GUY HTI HND HUN ISL IND IDN IRN IRQ
  IRL ISR ITA JAM JPN JOR KAZ KEN KIR PRK KOR KWT KGZ LAO LVA LBN LSO LBR LBY LIE
  LTU LUX MDG MWI MYS MDV MLI MLT MHL MRT MUS MEX FSM MDA MCO MNG MNE MAR MOZ MMR
  NAM NRU NPL NLD NZL NIC NER NGA MKD NOR OMN PAK PLW PAN PNG PRY PER PHL POL PRT
  QAT ROU RUS RWA KNA LCA VCT WSM SMR STP SAU SEN SRB SYC SLE SGP SVK SVN SLB SOM
  ZAF SSD ESP LKA SDN SUR SWE CHE SYR TJK TZA THA TLS TGO TON TTO TUN TUR TKM TUV
  UGA UKR ARE GBR USA URY UZB VUT VEN VNM YEM ZMB ZWE
`.trim().split(/\s+/);

/** Answerable on top of the UN members: two observer states and two widely recognised states. */
export const EXTRA_ANSWERS = ['VAT', 'PSE', 'TWN', 'XKX'];

export const CATALOG = {
  // ---------------------------------------------------------------- Africa
  DZA: c('Algeria', 'africa', ['Algiers', 36.7538, 3.0588, ['Alger']]),
  AGO: c('Angola', 'africa', ['Luanda', -8.8390, 13.2894]),
  BEN: c('Benin', 'africa', ['Porto-Novo', 6.4969, 2.6289, ['Cotonou']], ['Dahomey']),
  BWA: c('Botswana', 'africa', ['Gaborone', -24.6282, 25.9231]),
  BFA: c('Burkina Faso', 'africa', ['Ouagadougou', 12.3714, -1.5197], ['Upper Volta', 'Burkina']),
  BDI: c('Burundi', 'africa', ['Gitega', -3.4271, 29.9246, ['Bujumbura']]),
  CPV: c('Cabo Verde', 'africa', ['Praia', 14.9330, -23.5133], ['Cape Verde']),
  CMR: c('Cameroon', 'africa', ['Yaoundé', 3.8480, 11.5021]),
  CAF: c('Central African Republic', 'africa', ['Bangui', 4.3947, 18.5582], ['CAR']),
  TCD: c('Chad', 'africa', ["N'Djamena", 12.1348, 15.0557, ['Ndjamena']]),
  COM: c('Comoros', 'africa', ['Moroni', -11.7172, 43.2473]),
  COG: c('Republic of the Congo', 'africa', ['Brazzaville', -4.2634, 15.2429],
    ['Congo', 'Congo-Brazzaville', 'Congo Republic']),
  COD: c('DR Congo', 'africa', ['Kinshasa', -4.4419, 15.2663],
    ['Democratic Republic of the Congo', 'DRC', 'DR of the Congo', 'Congo-Kinshasa', 'Zaire']),
  CIV: c("Côte d'Ivoire", 'africa', ['Yamoussoukro', 6.8276, -5.2893, ['Abidjan']], ['Ivory Coast']),
  DJI: c('Djibouti', 'africa', ['Djibouti', 11.5880, 43.1450, ['Djibouti City']]),
  EGY: c('Egypt', 'africa', ['Cairo', 30.0444, 31.2357]),
  GNQ: c('Equatorial Guinea', 'africa', ['Ciudad de la Paz', 1.5889, 10.8225, ['Malabo', 'Oyala']]),
  ERI: c('Eritrea', 'africa', ['Asmara', 15.3229, 38.9251]),
  SWZ: c('Eswatini', 'africa', ['Mbabane', -26.3054, 31.1367, ['Lobamba']], ['Swaziland']),
  ETH: c('Ethiopia', 'africa', ['Addis Ababa', 9.0250, 38.7469], ['Abyssinia']),
  GAB: c('Gabon', 'africa', ['Libreville', 0.4162, 9.4673]),
  GMB: c('Gambia', 'africa', ['Banjul', 13.4549, -16.5790], ['The Gambia']),
  GHA: c('Ghana', 'africa', ['Accra', 5.6037, -0.1870]),
  GIN: c('Guinea', 'africa', ['Conakry', 9.6412, -13.5784], ['Guinea-Conakry']),
  GNB: c('Guinea-Bissau', 'africa', ['Bissau', 11.8817, -15.6178]),
  KEN: c('Kenya', 'africa', ['Nairobi', -1.2921, 36.8219]),
  LSO: c('Lesotho', 'africa', ['Maseru', -29.3151, 27.4869]),
  LBR: c('Liberia', 'africa', ['Monrovia', 6.3156, -10.8074]),
  LBY: c('Libya', 'africa', ['Tripoli', 32.8872, 13.1913]),
  MDG: c('Madagascar', 'africa', ['Antananarivo', -18.8792, 47.5079, ['Tana']]),
  MWI: c('Malawi', 'africa', ['Lilongwe', -13.9626, 33.7741]),
  MLI: c('Mali', 'africa', ['Bamako', 12.6392, -8.0029]),
  MRT: c('Mauritania', 'africa', ['Nouakchott', 18.0735, -15.9582]),
  MUS: c('Mauritius', 'africa', ['Port Louis', -20.1609, 57.5012]),
  MAR: c('Morocco', 'africa', ['Rabat', 34.0209, -6.8416]),
  MOZ: c('Mozambique', 'africa', ['Maputo', -25.9692, 32.5732]),
  NAM: c('Namibia', 'africa', ['Windhoek', -22.5609, 17.0658]),
  NER: c('Niger', 'africa', ['Niamey', 13.5116, 2.1254]),
  NGA: c('Nigeria', 'africa', ['Abuja', 9.0765, 7.3986]),
  RWA: c('Rwanda', 'africa', ['Kigali', -1.9441, 30.0619]),
  STP: c('São Tomé and Príncipe', 'africa', ['São Tomé', 0.3365, 6.7273], ['Sao Tome', 'Sao Tome and Principe']),
  SEN: c('Senegal', 'africa', ['Dakar', 14.7167, -17.4677]),
  SYC: c('Seychelles', 'africa', ['Victoria', -4.6191, 55.4513]),
  SLE: c('Sierra Leone', 'africa', ['Freetown', 8.4657, -13.2317]),
  SOM: c('Somalia', 'africa', ['Mogadishu', 2.0469, 45.3182]),
  ZAF: c('South Africa', 'africa', ['Pretoria', -25.7479, 28.2293, ['Tshwane', 'Cape Town', 'Bloemfontein']], ['RSA']),
  SSD: c('South Sudan', 'africa', ['Juba', 4.8594, 31.5713]),
  SDN: c('Sudan', 'africa', ['Khartoum', 15.5007, 32.5599]),
  TZA: c('Tanzania', 'africa', ['Dodoma', -6.1630, 35.7516, ['Dar es Salaam']]),
  TGO: c('Togo', 'africa', ['Lomé', 6.1256, 1.2254]),
  TUN: c('Tunisia', 'africa', ['Tunis', 36.8065, 10.1815]),
  UGA: c('Uganda', 'africa', ['Kampala', 0.3476, 32.5825]),
  ZMB: c('Zambia', 'africa', ['Lusaka', -15.3875, 28.3228]),
  ZWE: c('Zimbabwe', 'africa', ['Harare', -17.8252, 31.0335], ['Rhodesia']),
  // Africa — territories and partially recognised
  ESH: c('Western Sahara', 'africa', ['Laayoune', 27.1253, -13.1625, ['El Aaiún', 'El Aaiun']], ['Sahrawi Republic', 'SADR']),
  REU: c('Réunion', 'africa', ['Saint-Denis', -20.8823, 55.4504], ['Reunion']),
  MYT: c('Mayotte', 'africa', ['Mamoudzou', -12.7806, 45.2279]),
  SHN: c('Saint Helena', 'africa', ['Jamestown', -15.9244, -5.7181],
    ['St Helena', 'Saint Helena, Ascension and Tristan da Cunha', 'Ascension Island', 'Tristan da Cunha']),
  IOT: c('British Indian Ocean Territory', 'africa', null, ['BIOT', 'Chagos Islands', 'Diego Garcia']),
  ATF: c('French Southern and Antarctic Lands', 'africa', ['Port-aux-Français', -49.3500, 70.2167],
    ['French Southern Territories', 'TAAF', 'Kerguelen Islands']),

  // ---------------------------------------------------------------- Americas
  ATG: c('Antigua and Barbuda', 'americas', ["Saint John's", 17.1274, -61.8468, ["St. John's", "St John's"]], ['Antigua']),
  ARG: c('Argentina', 'americas', ['Buenos Aires', -34.6037, -58.3816]),
  BHS: c('Bahamas', 'americas', ['Nassau', 25.0480, -77.3554], ['The Bahamas']),
  BRB: c('Barbados', 'americas', ['Bridgetown', 13.0969, -59.6145]),
  BLZ: c('Belize', 'americas', ['Belmopan', 17.2510, -88.7590], ['British Honduras']),
  BOL: c('Bolivia', 'americas', ['Sucre', -19.0196, -65.2619, ['La Paz']]),
  BRA: c('Brazil', 'americas', ['Brasília', -15.7939, -47.8828], ['Brasil']),
  CAN: c('Canada', 'americas', ['Ottawa', 45.4215, -75.6972]),
  CHL: c('Chile', 'americas', ['Santiago', -33.4489, -70.6693, ['Santiago de Chile']]),
  COL: c('Colombia', 'americas', ['Bogotá', 4.7110, -74.0721]),
  CRI: c('Costa Rica', 'americas', ['San José', 9.9281, -84.0907]),
  CUB: c('Cuba', 'americas', ['Havana', 23.1136, -82.3666, ['La Habana']]),
  DMA: c('Dominica', 'americas', ['Roseau', 15.3092, -61.3794]),
  DOM: c('Dominican Republic', 'americas', ['Santo Domingo', 18.4861, -69.9312]),
  ECU: c('Ecuador', 'americas', ['Quito', -0.1807, -78.4678]),
  SLV: c('El Salvador', 'americas', ['San Salvador', 13.6929, -89.2182]),
  GRD: c('Grenada', 'americas', ["St. George's", 12.0561, -61.7488, ["Saint George's", "St George's"]]),
  GTM: c('Guatemala', 'americas', ['Guatemala City', 14.6349, -90.5069]),
  GUY: c('Guyana', 'americas', ['Georgetown', 6.8013, -58.1551], ['British Guiana']),
  HTI: c('Haiti', 'americas', ['Port-au-Prince', 18.5944, -72.3074]),
  HND: c('Honduras', 'americas', ['Tegucigalpa', 14.0723, -87.1921]),
  JAM: c('Jamaica', 'americas', ['Kingston', 17.9714, -76.7920]),
  MEX: c('Mexico', 'americas', ['Mexico City', 19.4326, -99.1332, ['Ciudad de México', 'CDMX']], ['México']),
  NIC: c('Nicaragua', 'americas', ['Managua', 12.1150, -86.2362]),
  PAN: c('Panama', 'americas', ['Panama City', 8.9824, -79.5199]),
  PRY: c('Paraguay', 'americas', ['Asunción', -25.2637, -57.5759]),
  PER: c('Peru', 'americas', ['Lima', -12.0464, -77.0428]),
  KNA: c('Saint Kitts and Nevis', 'americas', ['Basseterre', 17.3026, -62.7177], ['St Kitts and Nevis', 'St. Kitts and Nevis', 'Saint Kitts', 'St Kitts']),
  LCA: c('Saint Lucia', 'americas', ['Castries', 14.0101, -60.9875], ['St Lucia', 'St. Lucia']),
  VCT: c('Saint Vincent and the Grenadines', 'americas', ['Kingstown', 13.1587, -61.2248],
    ['St Vincent and the Grenadines', 'St. Vincent and the Grenadines', 'Saint Vincent', 'St Vincent']),
  SUR: c('Suriname', 'americas', ['Paramaribo', 5.8520, -55.2038], ['Surinam', 'Dutch Guiana']),
  TTO: c('Trinidad and Tobago', 'americas', ['Port of Spain', 10.6596, -61.5089, ['Port-of-Spain']], ['Trinidad']),
  USA: c('United States', 'americas', ['Washington, D.C.', 38.9072, -77.0369, ['Washington', 'Washington DC']],
    ['United States of America', 'USA', 'US', 'America', 'U.S.A.', 'U.S.']),
  URY: c('Uruguay', 'americas', ['Montevideo', -34.9011, -56.1645]),
  VEN: c('Venezuela', 'americas', ['Caracas', 10.4806, -66.9036]),
  // Americas — territories
  AIA: c('Anguilla', 'americas', ['The Valley', 18.2170, -63.0578]),
  ABW: c('Aruba', 'americas', ['Oranjestad', 12.5186, -70.0358]),
  BMU: c('Bermuda', 'americas', ['Hamilton', 32.2930, -64.7820]),
  BES: c('Caribbean Netherlands', 'americas', ['Kralendijk', 12.1443, -68.2655],
    ['Bonaire', 'Sint Eustatius', 'Saba', 'Bonaire, Sint Eustatius and Saba', 'BES Islands']),
  VGB: c('British Virgin Islands', 'americas', ['Road Town', 18.4286, -64.6185], ['BVI']),
  CYM: c('Cayman Islands', 'americas', ['George Town', 19.2869, -81.3674], ['Caymans']),
  CUW: c('Curaçao', 'americas', ['Willemstad', 12.1091, -68.9316], ['Curacao']),
  FLK: c('Falkland Islands', 'americas', ['Stanley', -51.6921, -57.8589, ['Port Stanley']], ['Falklands', 'Malvinas', 'Islas Malvinas']),
  GUF: c('French Guiana', 'americas', ['Cayenne', 4.9224, -52.3135], ['Guyane']),
  GRL: c('Greenland', 'americas', ['Nuuk', 64.1814, -51.6941, ['Godthåb']], ['Kalaallit Nunaat']),
  GLP: c('Guadeloupe', 'americas', ['Basse-Terre', 15.9985, -61.7255]),
  MTQ: c('Martinique', 'americas', ['Fort-de-France', 14.6161, -61.0588]),
  MSR: c('Montserrat', 'americas', ['Brades', 16.7928, -62.2106, ['Plymouth']]),
  PRI: c('Puerto Rico', 'americas', ['San Juan', 18.4655, -66.1057]),
  BLM: c('Saint Barthélemy', 'americas', ['Gustavia', 17.8962, -62.8498], ['St Barts', 'St. Barths', 'Saint Barthelemy']),
  MAF: c('Saint Martin', 'americas', ['Marigot', 18.0679, -63.0825], ['St Martin', 'Saint-Martin', 'French Saint Martin']),
  SPM: c('Saint Pierre and Miquelon', 'americas', ['Saint-Pierre', 46.7781, -56.1773], ['St Pierre and Miquelon']),
  SXM: c('Sint Maarten', 'americas', ['Philipsburg', 18.0260, -63.0458], ['Dutch Sint Maarten']),
  SGS: c('South Georgia and the South Sandwich Islands', 'americas', ['King Edward Point', -54.2833, -36.4950, ['Grytviken']], ['South Georgia']),
  TCA: c('Turks and Caicos Islands', 'americas', ['Cockburn Town', 21.4612, -71.1419], ['Turks and Caicos']),
  VIR: c('U.S. Virgin Islands', 'americas', ['Charlotte Amalie', 18.3419, -64.9307], ['US Virgin Islands', 'USVI', 'United States Virgin Islands']),

  // ---------------------------------------------------------------- Asia
  AFG: c('Afghanistan', 'asia', ['Kabul', 34.5553, 69.2075]),
  ARM: c('Armenia', 'asia', ['Yerevan', 40.1792, 44.4991]),
  AZE: c('Azerbaijan', 'asia', ['Baku', 40.4093, 49.8671]),
  BHR: c('Bahrain', 'asia', ['Manama', 26.2285, 50.5860]),
  BGD: c('Bangladesh', 'asia', ['Dhaka', 23.8103, 90.4125, ['Dacca']], ['East Pakistan']),
  BTN: c('Bhutan', 'asia', ['Thimphu', 27.4728, 89.6390]),
  BRN: c('Brunei', 'asia', ['Bandar Seri Begawan', 4.9031, 114.9398], ['Brunei Darussalam']),
  KHM: c('Cambodia', 'asia', ['Phnom Penh', 11.5564, 104.9282], ['Kampuchea']),
  CHN: c('China', 'asia', ['Beijing', 39.9042, 116.4074, ['Peking']], ["People's Republic of China", 'PRC']),
  GEO: c('Georgia', 'asia', ['Tbilisi', 41.7151, 44.8271, ['Tiflis']], ['Sakartvelo']),
  IND: c('India', 'asia', ['New Delhi', 28.6139, 77.2090, ['Delhi']], ['Bharat']),
  IDN: c('Indonesia', 'asia', ['Jakarta', -6.2088, 106.8456]),
  IRN: c('Iran', 'asia', ['Tehran', 35.6892, 51.3890, ['Teheran']], ['Persia', 'Islamic Republic of Iran']),
  IRQ: c('Iraq', 'asia', ['Baghdad', 33.3152, 44.3661]),
  // The government quarter (Givat Ram): the usual Old City coordinates fall
  // just over the Green Line, inside Palestine's shape.
  ISR: c('Israel', 'asia', ['Jerusalem', 31.7767, 35.2054]),
  JPN: c('Japan', 'asia', ['Tokyo', 35.6762, 139.6503], ['Nippon', 'Nihon']),
  JOR: c('Jordan', 'asia', ['Amman', 31.9454, 35.9284]),
  KAZ: c('Kazakhstan', 'asia', ['Astana', 51.1694, 71.4491, ['Nur-Sultan', 'Akmola']]),
  KWT: c('Kuwait', 'asia', ['Kuwait City', 29.3759, 47.9774]),
  KGZ: c('Kyrgyzstan', 'asia', ['Bishkek', 42.8746, 74.5698], ['Kirghizia', 'Kyrgyz Republic']),
  LAO: c('Laos', 'asia', ['Vientiane', 17.9757, 102.6331], ['Lao PDR']),
  LBN: c('Lebanon', 'asia', ['Beirut', 33.8938, 35.5018]),
  MYS: c('Malaysia', 'asia', ['Kuala Lumpur', 3.1390, 101.6869, ['Putrajaya', 'KL']]),
  MDV: c('Maldives', 'asia', ['Malé', 4.1755, 73.5093], ['The Maldives']),
  MNG: c('Mongolia', 'asia', ['Ulaanbaatar', 47.8864, 106.9057, ['Ulan Bator']]),
  MMR: c('Myanmar', 'asia', ['Naypyidaw', 19.7633, 96.0785, ['Nay Pyi Taw', 'Naypyitaw', 'Yangon', 'Rangoon']], ['Burma']),
  NPL: c('Nepal', 'asia', ['Kathmandu', 27.7172, 85.3240]),
  PRK: c('North Korea', 'asia', ['Pyongyang', 39.0392, 125.7625], ['DPRK', "Democratic People's Republic of Korea"]),
  OMN: c('Oman', 'asia', ['Muscat', 23.5880, 58.3829]),
  PAK: c('Pakistan', 'asia', ['Islamabad', 33.6844, 73.0479]),
  PSE: c('Palestine', 'asia', ['Ramallah', 31.9038, 35.2034, ['East Jerusalem']], ['State of Palestine', 'Palestinian Territories', 'West Bank', 'Gaza']),
  PHL: c('Philippines', 'asia', ['Manila', 14.5995, 120.9842], ['The Philippines']),
  QAT: c('Qatar', 'asia', ['Doha', 25.2854, 51.5310]),
  SAU: c('Saudi Arabia', 'asia', ['Riyadh', 24.7136, 46.6753], ['KSA']),
  SGP: c('Singapore', 'asia', ['Singapore', 1.2903, 103.8520]),
  KOR: c('South Korea', 'asia', ['Seoul', 37.5665, 126.9780], ['Korea', 'Republic of Korea', 'ROK']),
  LKA: c('Sri Lanka', 'asia', ['Sri Jayawardenepura Kotte', 6.8868, 79.9187, ['Kotte', 'Colombo']], ['Ceylon']),
  SYR: c('Syria', 'asia', ['Damascus', 33.5138, 36.2765]),
  TWN: c('Taiwan', 'asia', ['Taipei', 25.0330, 121.5654], ['Republic of China', 'ROC', 'Chinese Taipei', 'Formosa']),
  TJK: c('Tajikistan', 'asia', ['Dushanbe', 38.5598, 68.7870]),
  THA: c('Thailand', 'asia', ['Bangkok', 13.7563, 100.5018, ['Krung Thep']], ['Siam']),
  TLS: c('Timor-Leste', 'asia', ['Dili', -8.5569, 125.5603], ['East Timor']),
  TUR: c('Turkey', 'asia', ['Ankara', 39.9334, 32.8597], ['Türkiye']),
  TKM: c('Turkmenistan', 'asia', ['Ashgabat', 37.9601, 58.3261, ['Ashkhabad']]),
  ARE: c('United Arab Emirates', 'asia', ['Abu Dhabi', 24.4539, 54.3773], ['UAE', 'Emirates']),
  UZB: c('Uzbekistan', 'asia', ['Tashkent', 41.2995, 69.2401]),
  VNM: c('Vietnam', 'asia', ['Hanoi', 21.0278, 105.8342, ['Ha Noi']], ['Viet Nam']),
  YEM: c('Yemen', 'asia', ['Sanaa', 15.3694, 44.1910, ["Sana'a", 'Sana']]),
  // Asia — territories
  HKG: c('Hong Kong', 'asia', null),
  MAC: c('Macao', 'asia', null, ['Macau']),

  // ---------------------------------------------------------------- Europe
  ALB: c('Albania', 'europe', ['Tirana', 41.3275, 19.8187]),
  AND: c('Andorra', 'europe', ['Andorra la Vella', 42.5063, 1.5218]),
  AUT: c('Austria', 'europe', ['Vienna', 48.2082, 16.3738, ['Wien']]),
  BLR: c('Belarus', 'europe', ['Minsk', 53.9006, 27.5590], ['Byelorussia', 'White Russia']),
  BEL: c('Belgium', 'europe', ['Brussels', 50.8503, 4.3517, ['Bruxelles', 'Brussel']]),
  BIH: c('Bosnia and Herzegovina', 'europe', ['Sarajevo', 43.8563, 18.4131], ['Bosnia', 'Bosnia-Herzegovina']),
  BGR: c('Bulgaria', 'europe', ['Sofia', 42.6977, 23.3219]),
  HRV: c('Croatia', 'europe', ['Zagreb', 45.8150, 15.9819], ['Hrvatska']),
  CYP: c('Cyprus', 'europe', ['Nicosia', 35.1725, 33.3650, ['Lefkosia']]),
  CZE: c('Czechia', 'europe', ['Prague', 50.0755, 14.4378, ['Praha']], ['Czech Republic']),
  DNK: c('Denmark', 'europe', ['Copenhagen', 55.6761, 12.5683]),
  EST: c('Estonia', 'europe', ['Tallinn', 59.4370, 24.7536]),
  FIN: c('Finland', 'europe', ['Helsinki', 60.1699, 24.9384], ['Suomi']),
  FRA: c('France', 'europe', ['Paris', 48.8566, 2.3522], ['French Republic']),
  DEU: c('Germany', 'europe', ['Berlin', 52.5200, 13.4050], ['Deutschland']),
  GRC: c('Greece', 'europe', ['Athens', 37.9838, 23.7275, ['Athina']], ['Hellas']),
  HUN: c('Hungary', 'europe', ['Budapest', 47.4979, 19.0402]),
  ISL: c('Iceland', 'europe', ['Reykjavík', 64.1466, -21.9426]),
  IRL: c('Ireland', 'europe', ['Dublin', 53.3498, -6.2603], ['Republic of Ireland', 'Eire', 'Éire']),
  ITA: c('Italy', 'europe', ['Rome', 41.9028, 12.4964, ['Roma']], ['Italia']),
  XKX: c('Kosovo', 'europe', ['Pristina', 42.6629, 21.1655, ['Prishtina', 'Priština']], [], { iso2: 'XK' }),
  LVA: c('Latvia', 'europe', ['Riga', 56.9496, 24.1052]),
  LIE: c('Liechtenstein', 'europe', ['Vaduz', 47.1410, 9.5209]),
  LTU: c('Lithuania', 'europe', ['Vilnius', 54.6872, 25.2797]),
  LUX: c('Luxembourg', 'europe', ['Luxembourg', 49.6116, 6.1319, ['Luxembourg City']]),
  MLT: c('Malta', 'europe', ['Valletta', 35.8989, 14.5146]),
  MDA: c('Moldova', 'europe', ['Chișinău', 47.0105, 28.8638, ['Kishinev']]),
  MCO: c('Monaco', 'europe', ['Monaco', 43.7384, 7.4246, ['Monaco-Ville']]),
  MNE: c('Montenegro', 'europe', ['Podgorica', 42.4304, 19.2594]),
  NLD: c('Netherlands', 'europe', ['Amsterdam', 52.3676, 4.9041, ['The Hague']], ['Holland', 'The Netherlands']),
  MKD: c('North Macedonia', 'europe', ['Skopje', 41.9981, 21.4254], ['Macedonia']),
  NOR: c('Norway', 'europe', ['Oslo', 59.9139, 10.7522], ['Norge']),
  POL: c('Poland', 'europe', ['Warsaw', 52.2297, 21.0122, ['Warszawa']], ['Polska']),
  PRT: c('Portugal', 'europe', ['Lisbon', 38.7223, -9.1393, ['Lisboa']]),
  ROU: c('Romania', 'europe', ['Bucharest', 44.4268, 26.1025, ['București']], ['Rumania']),
  RUS: c('Russia', 'europe', ['Moscow', 55.7558, 37.6173, ['Moskva']], ['Russian Federation']),
  SMR: c('San Marino', 'europe', ['San Marino', 43.9356, 12.4473, ['City of San Marino']]),
  SRB: c('Serbia', 'europe', ['Belgrade', 44.7866, 20.4489, ['Beograd']]),
  SVK: c('Slovakia', 'europe', ['Bratislava', 48.1486, 17.1077], ['Slovak Republic']),
  SVN: c('Slovenia', 'europe', ['Ljubljana', 46.0569, 14.5058]),
  ESP: c('Spain', 'europe', ['Madrid', 40.4168, -3.7038], ['España', 'Espana']),
  SWE: c('Sweden', 'europe', ['Stockholm', 59.3293, 18.0686], ['Sverige']),
  CHE: c('Switzerland', 'europe', ['Bern', 46.9480, 7.4474, ['Berne']], ['Swiss Confederation', 'Schweiz', 'Suisse']),
  UKR: c('Ukraine', 'europe', ['Kyiv', 50.4501, 30.5234, ['Kiev']], ['The Ukraine']),
  GBR: c('United Kingdom', 'europe', ['London', 51.5074, -0.1278],
    ['UK', 'Great Britain', 'Britain', 'England', 'Scotland', 'Wales', 'Northern Ireland', 'United Kingdom of Great Britain and Northern Ireland']),
  VAT: c('Vatican City', 'europe', ['Vatican City', 41.9029, 12.4534], ['Vatican', 'Holy See', 'Vatican City State']),
  // Europe — territories
  ALA: c('Åland Islands', 'europe', ['Mariehamn', 60.0971, 19.9348], ['Aland', 'Åland']),
  FRO: c('Faroe Islands', 'europe', ['Tórshavn', 62.0107, -6.7741], ['Faroes', 'Faeroe Islands']),
  GIB: c('Gibraltar', 'europe', ['Gibraltar', 36.1408, -5.3536]),
  GGY: c('Guernsey', 'europe', ['Saint Peter Port', 49.4560, -2.5363, ['St Peter Port']]),
  IMN: c('Isle of Man', 'europe', ['Douglas', 54.1500, -4.4800]),
  JEY: c('Jersey', 'europe', ['Saint Helier', 49.1880, -2.1049, ['St Helier']]),
  SJM: c('Svalbard and Jan Mayen', 'europe', ['Longyearbyen', 78.2232, 15.6267], ['Svalbard', 'Jan Mayen', 'Spitsbergen']),

  // ---------------------------------------------------------------- Oceania
  AUS: c('Australia', 'oceania', ['Canberra', -35.2809, 149.1300], ['Oz']),
  FJI: c('Fiji', 'oceania', ['Suva', -18.1416, 178.4419]),
  KIR: c('Kiribati', 'oceania', ['Tarawa', 1.3290, 172.9790, ['South Tarawa', 'Bairiki']]),
  MHL: c('Marshall Islands', 'oceania', ['Majuro', 7.0897, 171.3803]),
  FSM: c('Micronesia', 'oceania', ['Palikir', 6.9248, 158.1611], ['Federated States of Micronesia', 'FSM']),
  NRU: c('Nauru', 'oceania', ['Yaren', -0.5467, 166.9211]),
  NZL: c('New Zealand', 'oceania', ['Wellington', -41.2865, 174.7762], ['NZ', 'Aotearoa']),
  PLW: c('Palau', 'oceania', ['Ngerulmud', 7.5006, 134.6242, ['Melekeok']], ['Belau']),
  PNG: c('Papua New Guinea', 'oceania', ['Port Moresby', -9.4438, 147.1803], ['PNG']),
  WSM: c('Samoa', 'oceania', ['Apia', -13.8333, -171.7667], ['Western Samoa']),
  SLB: c('Solomon Islands', 'oceania', ['Honiara', -9.4456, 159.9729], ['Solomons']),
  TON: c('Tonga', 'oceania', ["Nuku'alofa", -21.1393, -175.2049, ['Nukualofa', 'Nukuʻalofa']]),
  TUV: c('Tuvalu', 'oceania', ['Funafuti', -8.5211, 179.1983], ['Ellice Islands']),
  VUT: c('Vanuatu', 'oceania', ['Port Vila', -17.7334, 168.3273], ['New Hebrides']),
  // Oceania — territories
  ASM: c('American Samoa', 'oceania', ['Pago Pago', -14.2781, -170.7025]),
  CXR: c('Christmas Island', 'oceania', ['Flying Fish Cove', -10.4217, 105.6791]),
  CCK: c('Cocos (Keeling) Islands', 'oceania', ['West Island', -12.1883, 96.8290], ['Cocos Islands', 'Keeling Islands']),
  COK: c('Cook Islands', 'oceania', ['Avarua', -21.2078, -159.7750]),
  PYF: c('French Polynesia', 'oceania', ['Papeete', -17.5350, -149.5696], ['Tahiti']),
  GUM: c('Guam', 'oceania', ['Hagåtña', 13.4745, 144.7504, ['Agana']]),
  HMD: c('Heard Island and McDonald Islands', 'oceania', null, ['Heard Island']),
  NCL: c('New Caledonia', 'oceania', ['Nouméa', -22.2758, 166.4580], ['Nouvelle-Calédonie']),
  NIU: c('Niue', 'oceania', ['Alofi', -19.0595, -169.9187]),
  NFK: c('Norfolk Island', 'oceania', ['Kingston', -29.0545, 167.9663]),
  MNP: c('Northern Mariana Islands', 'oceania', ['Saipan', 15.2137, 145.7546, ['Capitol Hill']], ['Northern Marianas', 'CNMI']),
  PCN: c('Pitcairn Islands', 'oceania', ['Adamstown', -25.0663, -130.1004], ['Pitcairn']),
  TKL: c('Tokelau', 'oceania', null),
  WLF: c('Wallis and Futuna', 'oceania', ['Mata-Utu', -13.2825, -176.1764]),

  // ---------------------------------------------------------------- Antarctica
  ATA: c('Antarctica', 'antarctica', null),
};
