package test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import src.Frontend;
import src.ast.records.ProgramRecord;
import src.backend.memory.ClassLayout;
import src.backend.memory.FieldLayout;
import src.backend.memory.ObjectLayout;

public class TestObjectLayout {

    public static void main(String[] args) throws IOException {

        ObjectLayout objectLayout = new ObjectLayout();

        ProgramRecord ast = Frontend.loadProject(
            Path.of("src/source/backend/object_fields_test.jvs")
        );

        Map<String, ClassLayout> layouts =
            objectLayout.calculate(ast);

        for (ClassLayout layout : layouts.values()) {

            System.out.println("\nClasse: " + layout.className());

            for (FieldLayout field : layout.fields()) {

                System.out.println(
                    field.name()
                    + " | Tipo: " + field.type()
                    + " | Offset: " + field.offSet()
                    + " | Tamanho: " + field.size()
                );
            }

            System.out.println("Tamanho total: " + layout.size());
            System.out.println("Alinhamento: " + layout.alignment());
        }
    }
}