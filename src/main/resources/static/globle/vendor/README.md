# Vendored libraries for Globle

These are copied verbatim from the published npm tarballs so the game has no
build step and no third-party script host: the site's CSP is `script-src 'self'`,
which a CDN `<script>` would have to punch a hole in. They are loaded as classic
`<script>` tags (UMD, exposing `window.d3` and `window.topojson`) before the game's
ES modules.

| File | Package | Version | Licence |
| --- | --- | --- | --- |
| `d3.min.js` | [`d3`](https://www.npmjs.com/package/d3) (`dist/d3.min.js`) | 7.9.0 | ISC, see `d3.LICENSE.txt` |
| `topojson-client.min.js` | [`topojson-client`](https://www.npmjs.com/package/topojson-client) (`dist/topojson-client.min.js`) | 3.1.0 | ISC, see `topojson-client.LICENSE.txt` |

The tarballs' sha512 matched the registry's `dist.integrity` when they were copied:

- d3 7.9.0 `sha512-e1U46jVP+w7Iut8Jt8ri1YsPOvFpg46k+K8TpCb0P+zjCkjkPnV7WzfDJzMHy1LnA+wj5pLT1wjO901gLXeEhA==`
- topojson-client 3.1.0 `sha512-605uxS6bcYxGXw9qi62XyrV6Q3xwbndjachmNxu8HWTtVPxZfEJN9fd/SZS1Q54Sn2y0TMyMxFj/cJINqGHrKw==`

Only `d3-geo` is really used (orthographic projection, canvas paths, graticule,
great-circle interpolation, areas and centroids). The full bundle is shipped anyway
because it is the file d3 publishes, minified and cacheable, and swapping it for a
hand-rolled subset would be a build step to maintain for about 200 KB.

To upgrade: `npm pack d3@<version> topojson-client@<version>`, check the integrity
against `npm view <pkg>@<version> dist.integrity`, copy the `dist/*.min.js` files and
`LICENSE` over these, and update this table.
