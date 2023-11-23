package com.example.springContract.controllers;

import com.example.springContract.model.*;
import com.example.springContract.repository.*;
import com.example.springContract.service.FinishProductService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class FinishProductController {
    @NonNull
    private FinishProductRepository finishProductRepository;

    @Autowired
    private FinishProductService service;

    @NonNull
    private MaterialsRepository materialsRepository;
    @NonNull
    private ManufactureRepository manufactureRepository;
    @NonNull
    private WarehouseRepository warehouseRepository;
    @NonNull
    private GroupMaterialsRepository groupMaterialsRepository;


    @GetMapping("/finishProduct")
    public String finishProduct(Model model, String id, Optional<Integer> man, String mat, LocalDate begin, LocalDate end) {
        List<FinishedProduct> list = new ArrayList<>();
        if (!Objects.equals(mat, "") && mat != null && man.isPresent()) {
            List<FinishedProduct> materials = service.getByMaterials(mat);
            List<FinishedProduct> manufacture = service.getByMan(man.get());
            for (FinishedProduct material : materials) {
                for (FinishedProduct finishedProduct : manufacture) {
                    if (material == finishedProduct) {
                        list.add(material);
                    }
                }
            }
        } else if (!Objects.equals(id, "") && id != null) {
            list = service.getByNameOrId(id);
        } else if (man.isPresent()) {
            int i = man.get();
            list = service.getByMan(i);
        } else if (!Objects.equals(mat, "") && mat != null) {
            list = service.getByMaterials(mat);
        } else {
            list = service.getAllProduct();
        }

        List<FinishedProduct> time = service.getByDate(begin, end);
        boolean flag = false;
        int size = list.size();
        if (!time.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                flag = false;
                for (FinishedProduct finishedProduct : time) {
                    if (list.get(i) == finishedProduct) {
                        flag = true;
                        break;
                    }
                }
                if (!flag) {
                    list.remove(i);
                    i--;
                }
            }
        }else {
            if(begin!=null){
                list.clear();
            }
        }

        model.addAttribute("finishProduct", list);

        return "finishProduct";
    }

    @GetMapping("/finishProduct/add")
    public String finisProductAdd(Model model){
        List<Materials> materials=materialsRepository.findAll();
        model.addAttribute("materials",materials);
        model.addAttribute("warehouse",warehouseRepository.findAll());
        model.addAttribute("man",manufactureRepository.findAll());
        return "product-add";
    }
    @PostMapping("/finishProduct/add")
    public String finishProductAddPost(Model model, @RequestParam Optional<Integer> id,@RequestParam String nameProduct,
                                       @RequestParam("man") String manufacture,@RequestParam String warehouse ,
                                       @RequestParam Optional<Integer>quantity , @RequestParam LocalDate date,
                                       @RequestParam("materials")String[] materials){

        Manufacture manufactureById=manufactureRepository.findById(Integer.parseInt(manufacture)).orElseThrow();
        TV_warehouse warehouseById=warehouseRepository.findById(Integer.parseInt(warehouse)).orElseThrow();
        FinishedProduct finishedProduct=new FinishedProduct(id.orElseThrow(),quantity.orElseThrow(),nameProduct,date,manufactureById,warehouseById);
        service.save(finishedProduct);
        FinishedProduct finishedProduct1=finishProductRepository.findById(id.orElseThrow()).orElseThrow();
        insertGroupMat(materials, finishedProduct1);
        if(warehouseById.getLast_receipt_date().isBefore(date)){
        warehouseById.setLast_receipt_date(date);
        warehouseRepository.save(warehouseById);}
        return "redirect:/finishProduct";
    }
    @GetMapping("/finishProduct/{id}")
    public String finishProductId(Model model, @PathVariable int id) {
        Optional<FinishedProduct> finishedProduct = finishProductRepository.findById(id);
        ArrayList<FinishedProduct> finishedProducts = new ArrayList<>();
        finishedProduct.ifPresent(finishedProducts::add);
        model.addAttribute("finishProduct", finishedProducts);
        return "detailsFinishProduct";
    }
    @PostMapping("/finishProduct/{id}/remove")
    public String removeProduct(Model model,@PathVariable int id){
        FinishedProduct finishedProduct=finishProductRepository.findById(id).orElseThrow();
        int i=finishedProduct.getTv_warehouse().getId();
        finishProductRepository.delete(finishedProduct);
        TV_warehouse warehouse=warehouseRepository.findById(i).orElseThrow();
        warehouse.setLast_receipt_date(finishProductRepository.findMaxDate(i));
        warehouseRepository.save(warehouse);
        return "redirect:/finishProduct";
    }
   @GetMapping("/finishProduct/{id}/change")
   public String changeProduct(Model model,@PathVariable int id){
        if(!finishProductRepository.existsById(id)){
            return "redirect:finishProduct";
        }
        Optional<FinishedProduct> optional=finishProductRepository.findById(id);
        List<FinishedProduct> list=new ArrayList<>();
        optional.ifPresent(list::add);
        model.addAttribute("product",list);
        model.addAttribute("man",manufactureRepository.findAll());
       List<Materials> materials=materialsRepository.findAll();
       model.addAttribute("materials",materials);
        return "product-change";
   }
   @PostMapping("/finishProduct/{id}/change")
    public String changePost(Model model,@PathVariable int id,@RequestParam String nameProduct,@RequestParam String man,
   @RequestParam LocalDate data,@RequestParam String[] materials ){
        FinishedProduct finishedProduct=finishProductRepository.findById(id).orElseThrow();
        if(!finishedProduct.getNameProduct().equals(nameProduct)&& !nameProduct.isEmpty()){
            finishedProduct.setNameProduct(nameProduct);
        }
        Manufacture manufacture=manufactureRepository.findById(Integer.parseInt(man)).orElseThrow();
        if(finishedProduct.getManufacture()!=manufacture){
            finishedProduct.setManufacture(manufacture);
        }
        if(finishedProduct.getDateCreation()!=data){
            finishedProduct.setDateCreation(data);
        }
       List<GroupMaterials> groupMaterials=groupMaterialsRepository.findByFinishedProduct(finishedProduct);
        groupMaterialsRepository.deleteAll(groupMaterials);
       insertGroupMat(materials, finishedProduct);
       finishProductRepository.save(finishedProduct);
        return "redirect:/finishProduct";
   }

    private void insertGroupMat(@RequestParam String[] materials, FinishedProduct finishedProduct) {
        List<Materials> materialsList=new ArrayList<>();
        for (var s :
                materials) {
            materialsList.add(materialsRepository.findById(Integer.parseInt(s)).orElseThrow());
        }
        for (Materials material : materialsList) {
            GroupMaterials groupMaterialsNew = new GroupMaterials(finishedProduct, material);
            groupMaterialsRepository.save(groupMaterialsNew);
        }
    }
}
