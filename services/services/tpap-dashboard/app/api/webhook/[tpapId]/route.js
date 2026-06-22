import { addLog } from '@/lib/store';
import { NextResponse } from 'next/server';

export async function POST(request, { params }) {
  const { tpapId } = await params;
  try {
    const payload = await request.json();
    addLog(tpapId, payload);
    return NextResponse.json({ status: 'received' }, { status: 200 });
  } catch (err) {
    return NextResponse.json({ error: 'invalid payload' }, { status: 400 });
  }
}
