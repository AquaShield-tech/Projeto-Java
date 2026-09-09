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
        Scanner scan = new Scanner(System.in);
        Integer qtdAlertasNormal = 0;
        Integer qtdAlertasModerado = 0;
        Integer qtdAlertasCritico = 0;

        System.out.println("Iniciando as leituras diárias...");
        System.out.println("Quantas simulações você deseja fazer?");

        int quantidade = scan.nextInt();
        simularValores(quantidade);

        ArrayList<Integer> leiturasCPU = leituras.get(0);
        ArrayList<Integer> leiturasDisco = leituras.get(1);
        ArrayList<Integer> leiturasMemoria = leituras.get(2);

        for (int i = 0; i < leiturasCPU.size(); i++) {
            if (leiturasCPU.get(i) > 80) {
                qtdAlertasCritico++;
                System.out.println("A EXECUÇÃO FOI PAUSADA DEVIDO A UM ALERTA CRÍTICO!! Verifique sua CPU!");
                break;
            } else if (leiturasCPU.get(i) > 70) {
                qtdAlertasModerado++;
            } else if (leiturasCPU.get(i) > 50) {
                qtdAlertasNormal++;
            }

            if (leiturasDisco.get(i) > 80) {
                qtdAlertasCritico++;
                System.out.println("A EXECUÇÃO FOI PAUSADA DEVIDO A UM ALERTA CRÍTICO!! Verifique seu Disco!");
                break;
            } else if (leiturasDisco.get(i) > 60) {
                qtdAlertasModerado++;
            } else if (leiturasDisco.get(i) > 40) {
                qtdAlertasNormal++;
            }

            if (leiturasMemoria.get(i) > 80) {
                qtdAlertasCritico++;
                System.out.println("A EXECUÇÃO FOI PAUSADA DEVIDO A UM ALERTA CRÍTICO!! Verifique sua Memória!");
                break;
            } else if (leiturasMemoria.get(i) > 60) {
                qtdAlertasModerado++;
            } else if (leiturasMemoria.get(i) > 40) {
                qtdAlertasNormal++;
            }
        }

        System.out.println("\nO resumo do seu dia foi: \n"
                + "Quantidade de alertas Normal: " + qtdAlertasNormal + "\n"
                + "Quantidade de alertas Moderado: " + qtdAlertasModerado + "\n"
                + "Quantidade de alertas Crítico: " + qtdAlertasCritico);

        System.out.println("Valores lidos no dia:\n" +
                "CPU: "+leituras.get(0)
                +"\nDisco: "+leituras.get(1)
                +"\nMemória: "+leituras.get(2)
        );

        scan.close();
    }
}
