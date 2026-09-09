package school.sptech;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class Eta {
    ArrayList<ArrayList> leituras = new ArrayList<>();
    String alerta;

    ArrayList simularValores(Integer qtdVezes){
        ArrayList<Integer>  cpuUse = new ArrayList<>();
        ArrayList<Integer>  discUse = new ArrayList<>();
        ArrayList<Integer>  memoryUse = new ArrayList<>();

        for (int i = 0; i < qtdVezes; i++) {
            cpuUse.add(ThreadLocalRandom.current().nextInt(0,101));
            discUse.add(ThreadLocalRandom.current().nextInt(0,101));
            memoryUse.add(ThreadLocalRandom.current().nextInt(0,101));
        }
        leituras.add(cpuUse);
        leituras.add(discUse);
        leituras.add(memoryUse);

        return leituras;
    }

}
