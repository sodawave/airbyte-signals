import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import React from "react";

import { PuentePanel, PuentePolicy } from "./PuentePanel";

const mockUpdateConnection = jest.fn();

jest.mock("hooks/services/useConnectionHook", () => ({
  useUpdateConnection: () => ({ mutateAsync: mockUpdateConnection, isLoading: false }),
}));

jest.mock("components/ui/Card", () => ({
  Card: ({ title, children }: React.PropsWithChildren<{ title: string }>) => (
    <section>
      <h2>{title}</h2>
      {children}
    </section>
  ),
}));

jest.mock("components/ui/Button", () => ({
  Button: ({ children, onClick }: React.PropsWithChildren<{ onClick: () => void }>) => (
    <button onClick={onClick}>{children}</button>
  ),
}));

const policy: PuentePolicy = {
  mode: "paper",
  rows: [
    {
      sourceSymbol: "DAX",
      canonicalInstrument: "DE40",
      destinationSymbol: "GER40",
      contractFactor: 1,
      scale: 2,
    },
    {
      sourceSymbol: "ES",
      canonicalInstrument: "US500",
      exclusionReason: "future_not_supported",
      contractFactor: 1,
      scale: 1,
    },
  ],
};

describe("PuentePanel", () => {
  beforeEach(() => {
    mockUpdateConnection.mockReset();
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        operations: [
          {
            id: "journal-1",
            sourceFillId: "paper-es-1",
            status: "skipped",
            reason: "future_not_supported",
            replicaAt: "2026-01-01T00:00:01Z",
            desfaseMs: 1000,
          },
        ],
      }),
    });
  });

  it("shows and saves the Spanish crosswalk, quota and operation journal", async () => {
    render(<PuentePanel connectionId="connection-1" policy={policy} />);

    expect(screen.getByText("Papel (sin límite)")).toBeInTheDocument();
    expect(screen.getByDisplayValue("GER40")).toBeInTheDocument();
    expect(await screen.findByText("future_not_supported")).toBeInTheDocument();
    expect(screen.getByText("1000 ms")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Escala DAX"), { target: { value: "3" } });
    fireEvent.click(screen.getByText("Guardar cruce"));

    await waitFor(() =>
      expect(mockUpdateConnection).toHaveBeenCalledWith(
        expect.objectContaining({
          connectionId: "connection-1",
          puentePolicy: expect.objectContaining({
            rows: expect.arrayContaining([expect.objectContaining({ sourceSymbol: "DAX", scale: 3 })]),
          }),
        })
      )
    );
  });
});
