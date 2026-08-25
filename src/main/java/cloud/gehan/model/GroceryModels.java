package cloud.gehan.model;

import java.util.ArrayList;
import java.util.List;

public class GroceryModels {
    public static class Root {
        public Grocery grocery = new Grocery();
    }
    public static class Grocery {
        public List<String> list = new ArrayList<>();
        public List<String> pantry = new ArrayList<>();
    }
}
