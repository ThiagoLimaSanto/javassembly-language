package test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import compiler.Frontend;
import compiler.ast.records.ProgramRecord;
import compiler.backend.memory.ClassLayout;
import compiler.backend.memory.FieldLayout;
import compiler.backend.memory.ObjectLayout;

public class TestObjectLayout {

    public static void main(String[] args) throws IOException {

        ObjectLayout objectLayout = new ObjectLayout();

        ProgramRecord ast = Frontend.loadProject(
            Path.of("test/fixtures/backend/accept/accept_object_arrays_fields.jvs")
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
