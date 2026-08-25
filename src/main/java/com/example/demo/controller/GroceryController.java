package com.example.demo.controller;

import com.example.demo.model.GroceryModels.Root;
import com.example.demo.service.GroceryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grocery")
public class GroceryController {
    private final GroceryService svc;
    public GroceryController(GroceryService svc) { this.svc = svc; }

    // list
    @PostMapping("/list/add")    public void addList(@RequestBody List<String> items){ svc.addToList(items); }
    @PostMapping("/list/clear")  public void clearList(){ svc.clearList(); }
    @GetMapping ("/list")        public List<String> grabList(){ return svc.grabList(); }
    @PostMapping("/list/remove") public void removeList(@RequestBody List<String> items){ svc.removeFromList(items); }

    // pantry
    @PostMapping("/pantry/add")    public void addPantry(@RequestBody List<String> items){ svc.addToPantry(items); }
    @PostMapping("/pantry/remove") public void removePantry(@RequestBody List<String> items){ svc.removeFromPantry(items); }
    @GetMapping ("/pantry/has")    public boolean hasPantry(@RequestParam String item){ return svc.checkPantry(item); }

    // debug (optional)
    @GetMapping("/dump") public Root dump(){ return svc.dump(); }
}
