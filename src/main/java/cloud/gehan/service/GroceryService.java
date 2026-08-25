package cloud.gehan.service;

import cloud.gehan.model.GroceryModels.Grocery;
import cloud.gehan.model.GroceryModels.Root;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@Service
public class GroceryService {

    private final ObjectMapper om = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final File file;

    public GroceryService(@Value("${grocery.file:grocery.json}") String filePath) {
        this.file = new File(filePath);
    }

    // --- IO helpers ---
    private synchronized Root load() {
        try {
            if (!file.exists() || Files.size(file.toPath()) == 0) {
                Root r = new Root();
                save(r);
                return r;
            }
            return om.readValue(file, Root.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + file.getAbsolutePath(), e);
        }
    }

    private synchronized void save(Root root) {
        try {
            om.writerWithDefaultPrettyPrinter().writeValue(file, root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save " + file.getAbsolutePath(), e);
        }
    }

    // --- list ---
    // addToList(items[])
    public void addToList(List<String> items) {
        Root r = load();
        r.grocery.list.addAll(items);
        save(r);
    }

    // clearList()
    public void clearList() {
        Root r = load();
        r.grocery.list = new ArrayList<>();
        save(r);
    }

    // grabList()
    public List<String> grabList() {
        return new ArrayList<>(load().grocery.list);
    }

    // removeFromList(items[])
    public void removeFromList(List<String> items) {
        Set<String> toRemove = new HashSet<>(items);
        Root r = load();
        r.grocery.list.removeIf(toRemove::contains);
        save(r);
    }

    // --- pantry ---
    // add items to pantry from a list
    public void addToPantry(List<String> items) {
        Root r = load();
        r.grocery.pantry.addAll(items);
        save(r);
    }

    // remove items from pantry from a list
    public void removeFromPantry(List<String> items) {
        Set<String> toRemove = new HashSet<>(items);
        Root r = load();
        r.grocery.pantry.removeIf(toRemove::contains);
        save(r);
    }

    // check pantry: if item in pantry return true/false
    public boolean checkPantry(String item) {
        return load().grocery.pantry.contains(item);
    }

    // (optional) expose whole JSON for debugging
    public Root dump() {
        return load();
    }

    // (optional) replace whole JSON
    public void replace(Root newRoot) {
        save(newRoot == null ? new Root() : newRoot);
    }
}
