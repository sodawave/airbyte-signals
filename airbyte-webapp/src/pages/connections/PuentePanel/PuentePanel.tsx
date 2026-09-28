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
  const [journalLoading, setJournalLoading] = useState(false);
  const [journalError, setJournalError] = useState<string>();
  const { mutateAsync: updateConnection, isLoading } = useUpdateConnection();

  useEffect(() => setDraft(policy), [policy]);
  useEffect(() => {
    if (!policy) {
      return;
    }
    const controller = new AbortController();
    setJournalLoading(true);
    setJournalError(undefined);
    fetch("/api/v1/connections/puente/journal", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ connectionId }),
      signal: controller.signal,
    })
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error("No se pudo cargar el diario"))))
      .then((response: { operations: JournalRow[] }) => {
        setOperations(response.operations);
        setJournalLoading(false);
      })
      .catch((error: Error) => {
        if (error.name !== "AbortError") {
          setJournalError("No se pudo cargar el diario de operaciones.");
          setJournalLoading(false);
        }
      });
    return () => controller.abort();
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
  const policyValid = draft.rows.every(
    (row) =>
      Number.isFinite(row.contractFactor) &&
      row.contractFactor > 0 &&
      Number.isFinite(row.scale) &&
      row.scale > 0 &&
      Boolean(row.destinationSymbol) !== Boolean(row.exclusionReason)
  );
  const statusLabel: Record<string, string> = {
    accepted: "Aceptada",
    rejected: "Rechazada",
    duplicate: "Duplicada",
    skipped: "Omitida",
  };
  const reasonLabel: Record<string, string> = {
    future_not_supported: "Futuro no admitido",
    destination_unavailable: "Instrumento no disponible en destino",
    missing_map: "Sin cruce guardado",
    missing_fill_id: "Fill de origen ausente",
    missing_destination_symbol: "Símbolo de destino ausente",
    invalid_record_data: "Registro de origen inválido",
    invalid_origin_timestamp: "Marca temporal de origen inválida",
    already_accepted: "Ya aceptada",
    already_in_flight: "Ya está en curso",
    quota_exhausted: "Cuota agotada",
    unsupported_symbol: "Símbolo no admitido",
    canonical_mismatch: "Instrumento canónico incompatible",
    destination_delivery_failed: "Falló el envío al destino",
    destination_rejected: "Destino rechazó la operación",
    destination_finished_without_ack: "Destino terminó sin confirmar",
  };

  return (
    <>
      <Card title="Puente: cruce de instrumentos">
        <p>
          Modo: <strong>{draft.mode === "paper" ? "Papel (sin límite)" : "En vivo"}</strong>
          {draft.mode === "live" && <> · Cuota restante: {draft.liveQuota == null ? "Sin límite" : draft.liveQuota}</>}
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
                    min="0.000001"
                    step="any"
                    value={row.contractFactor}
                    onChange={(event) => updateRow(index, { contractFactor: Number(event.target.value) })}
                  />
                </td>
                <td>
                  <input
                    aria-label={`Escala ${row.sourceSymbol}`}
                    type="number"
                    min="0.000001"
                    step="any"
                    value={row.scale}
                    onChange={(event) => updateRow(index, { scale: Number(event.target.value) })}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!policyValid && <p role="alert">Factor y escala deben ser positivos; cada fila debe mapearse o excluirse.</p>}
        <Button
          isLoading={isLoading}
          disabled={!policyValid}
          onClick={() => updateConnection({ connectionId, puentePolicy: draft } as never)}
        >
          Guardar cruce
        </Button>
      </Card>
      <Card title="Diario de operaciones">
        {journalLoading && <p>Cargando diario…</p>}
        {journalError && <p role="alert">{journalError}</p>}
        {!journalLoading && !journalError && operations.length === 0 && <p>No hay operaciones.</p>}
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
            {!journalLoading &&
              !journalError &&
              operations.map((operation) => (
                <tr key={operation.id}>
                  <td>{operation.sourceFillId ?? "—"}</td>
                  <td>{statusLabel[operation.status] ?? operation.status}</td>
                  <td>{operation.reason ? reasonLabel[operation.reason] ?? operation.reason : "—"}</td>
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
