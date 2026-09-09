package school.sptech;

import java.util.ArrayList;
import java.util.Scanner;
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

    void consultarValores(){
        System.out.println("Os valores das leituras:\n" +
                "CPU: "+leituras.get(0)
                +"\nDisco: "+leituras.get(1)
                +"\nMemória: "+leituras.get(2)
        );
    }

    void simularDia(){
        ArrayList<Integer> leiturasCPU = leituras.get(0);
        ArrayList<Integer> leiturasDisco = leituras.get(1);
        ArrayList<Integer> leiturasMemoria = leituras.get(2);
        Integer qtdAlertasNormal = 0;
        Integer qtdAlertasModerado = 0;
        Integer qtdAlertasCritico = 0;
        Scanner scan = new Scanner(System.in);

        System.out.println("Iniciando as leituras diárias...\n" +
                "Caso tenha um alerta iremos avisar!");

        simularValores(10);

        for (int i = 0; i < leiturasCPU.size(); i++) {
            if (leiturasCPU.get(i) > 50){
                qtdAlertasNormal++;
            } else if (leiturasCPU.get(i) > 70) {
                qtdAlertasModerado++;
            } else if (leiturasCPU.get(i) > 80) {
                qtdAlertasCritico++;
            }
        }
    }
}
