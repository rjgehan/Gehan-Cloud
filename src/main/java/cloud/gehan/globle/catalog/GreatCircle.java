package cloud.gehan.globle.catalog;

/** Distances on a spherical Earth. */
public final class GreatCircle {

    /** The IUGG mean radius. A sphere is within about 0.5% of the ellipsoid everywhere. */
    static final double EARTH_RADIUS_KM = 6371.0088;

    private GreatCircle() {
    }

    /**
     * Great-circle distance by the haversine formula, which unlike the spherical law
     * of cosines stays accurate for points a few kilometres apart.
     */
    public static double km(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        // Rounding can push h a hair past 1 for antipodal points, and asin(>1) is NaN.
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1.0, h)));
    }
}
