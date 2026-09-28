import React, { useEffect, useState } from "react";

import { Button } from "components/ui/Button";
import { Card } from "components/ui/Card";

import { useUpdateConnection } from "hooks/services/useConnectionHook";

export interface PuenteCrosswalkRow {
  sourceSymbol: string;
  canonicalInstrument: string;
  destinationSymbol?: string;
  exclusionReason?: string;
  contractFactor: number;
  scale: number;
}

export interface PuentePolicy {
  mode: "paper" | "live";
  liveQuota?: number;
  rows: PuenteCrosswalkRow[];
}

interface JournalRow {
  id: string;
  sourceFillId?: string;
  status: string;
  reason?: string;
  originAt?: string;
  replicaAt: string;
  desfaseMs?: number;
}

interface PuentePanelProps {
  connectionId: string;
  policy?: PuentePolicy;
}

export const PuentePanel: React.FC<PuentePanelProps> = ({ connectionId, policy }) => {
  const [draft, setDraft] = useState(policy);
  const [operations, setOperations] = useState<JournalRow[]>([]);
  const { mutateAsync: updateConnection, isLoading } = useUpdateConnection();

  useEffect(() => setDraft(policy), [policy]);
  useEffect(() => {
    if (!policy) {
      return;
    }
    fetch("/api/v1/connections/puente/journal", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ connectionId }),
    })
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error("No se pudo cargar el diario"))))
      .then((response: { operations: JournalRow[] }) => setOperations(response.operations))
      .catch(() => setOperations([]));
  }, [connectionId, policy]);

  if (!draft) {
    return null;
  }

  const updateRow = (index: number, patch: Partial<PuenteCrosswalkRow>) => {
    setDraft({
      ...draft,
      rows: draft.rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)),
    });
  };

  return (
    <>
      <Card title="Puente: cruce de instrumentos">
        <p>
          Modo: <strong>{draft.mode === "paper" ? "Papel (sin límite)" : "En vivo"}</strong>
          {draft.mode === "live" && <> · Cuota restante: {draft.liveQuota ?? 0}</>}
        </p>
        <table>
          <thead>
            <tr>
              <th>Origen</th>
              <th>Instrumento canónico</th>
              <th>Destino</th>
              <th>Exclusión</th>
              <th>Factor</th>
              <th>Escala</th>
            </tr>
          </thead>
          <tbody>
            {draft.rows.map((row, index) => (
              <tr key={row.sourceSymbol}>
                <td>{row.sourceSymbol}</td>
                <td>{row.canonicalInstrument}</td>
                <td>
                  <input
                    aria-label={`Destino ${row.sourceSymbol}`}
                    value={row.destinationSymbol ?? ""}
                    onChange={(event) => updateRow(index, { destinationSymbol: event.target.value || undefined })}
                  />
                </td>
                <td>
                  <input
                    aria-label={`Exclusión ${row.sourceSymbol}`}
                    value={row.exclusionReason ?? ""}
                    onChange={(event) => updateRow(index, { exclusionReason: event.target.value || undefined })}
                  />
                </td>
                <td>
                  <input
                    aria-label={`Factor ${row.sourceSymbol}`}
                    type="number"
                    min="0"
                    step="any"
                    value={row.contractFactor}
                    onChange={(event) => updateRow(index, { contractFactor: Number(event.target.value) })}
                  />
                </td>
                <td>
                  <input
                    aria-label={`Escala ${row.sourceSymbol}`}
                    type="number"
                    min="0"
                    step="any"
                    value={row.scale}
                    onChange={(event) => updateRow(index, { scale: Number(event.target.value) })}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Button isLoading={isLoading} onClick={() => updateConnection({ connectionId, puentePolicy: draft } as never)}>
          Guardar cruce
        </Button>
      </Card>
      <Card title="Diario de operaciones">
        <table>
          <thead>
            <tr>
              <th>Fill de origen</th>
              <th>Estado</th>
              <th>Motivo</th>
              <th>Réplica</th>
              <th>Desfase</th>
            </tr>
          </thead>
          <tbody>
            {operations.map((operation) => (
              <tr key={operation.id}>
                <td>{operation.sourceFillId ?? "—"}</td>
                <td>{operation.status}</td>
                <td>{operation.reason ?? "—"}</td>
                <td>{operation.replicaAt}</td>
                <td>{operation.desfaseMs == null ? "—" : `${operation.desfaseMs} ms`}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </>
  );
};
